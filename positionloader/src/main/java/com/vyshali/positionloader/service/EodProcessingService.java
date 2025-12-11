package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.config.AppConfig;
import com.fxanalyzer.positionloader.config.LoaderProperties;
import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.exception.DuplicateSnapshotException;
import com.fxanalyzer.positionloader.exception.EodProcessingException;
import com.fxanalyzer.positionloader.repository.BatchRepository;
import com.fxanalyzer.positionloader.repository.EodRepository;
import com.fxanalyzer.positionloader.repository.PositionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * End-of-day position processing service.
 * 
 * Handles:
 * - EOD batch creation and activation (blue/green deployment)
 * - Duplicate detection (Phase 4 #19)
 * - Late EOD processing (Phase 4 #21)
 * - Holiday checks (Phase 4 #20)
 * - Batch rollback
 */
@Service
public class EodProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(EodProcessingService.class);
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final EodRepository eodRepository;
    private final MspmClientService mspmClient;
    private final DuplicateDetectionService duplicateDetectionService;
    private final HolidayService holidayService;
    private final LoaderProperties loaderProperties;
    private final MeterRegistry meterRegistry;
    
    public EodProcessingService(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            EodRepository eodRepository,
            MspmClientService mspmClient,
            DuplicateDetectionService duplicateDetectionService,
            HolidayService holidayService,
            LoaderProperties loaderProperties,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.eodRepository = eodRepository;
        this.mspmClient = mspmClient;
        this.duplicateDetectionService = duplicateDetectionService;
        this.holidayService = holidayService;
        this.loaderProperties = loaderProperties;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Process end-of-day positions for an account.
     * 
     * Steps:
     * 1. Check idempotency (already processed?)
     * 2. Check for holiday
     * 3. Fetch positions from MSPM
     * 4. Check for duplicates (Phase 4 #19)
     * 5. Create staging batch
     * 6. Insert positions
     * 7. Activate batch (archive previous)
     * 8. Update EOD run status
     */
    @Transactional
    public void processEod(int accountId, LocalDate businessDate) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            // Idempotency check
            if (eodRepository.isAlreadyCompleted(accountId, businessDate)) {
                log.info("EOD already completed for account {} on {}", accountId, businessDate);
                return;
            }
            
            // Holiday check (Phase 4 #20)
            if (holidayService.isHoliday(businessDate)) {
                log.info("Skipping EOD for account {} - {} is a holiday", accountId, businessDate);
                eodRepository.updateStatus(accountId, businessDate, AppConfig.EOD_STATUS_SKIPPED);
                return;
            }
            
            // Mark as in progress
            eodRepository.createOrUpdate(accountId, businessDate, AppConfig.EOD_STATUS_IN_PROGRESS);
            
            // Fetch positions from MSPM
            List<PositionDto> positions = mspmClient.fetchPositions(accountId, businessDate);
            log.info("Fetched {} positions from MSPM for account {}", positions.size(), accountId);
            
            // Duplicate detection (Phase 4 #19)
            if (loaderProperties.featureFlags().duplicateDetectionEnabled()) {
                checkForDuplicates(accountId, businessDate, positions);
            }
            
            // Create new batch
            int newBatchId = batchRepository.getNextBatchId(accountId);
            batchRepository.createBatch(accountId, newBatchId, businessDate, AppConfig.BATCH_STATUS_STAGING);
            
            // Insert positions in batches
            int batchSize = loaderProperties.batchSize();
            for (int i = 0; i < positions.size(); i += batchSize) {
                List<PositionDto> batch = positions.subList(
                    i, Math.min(i + batchSize, positions.size()));
                positionRepository.insertBatch(batch, newBatchId);
            }
            
            // Update position count
            batchRepository.updatePositionCount(accountId, newBatchId, positions.size());
            
            // Activate new batch (archives previous)
            activateBatch(accountId, newBatchId);
            
            // Mark EOD as completed
            eodRepository.markCompleted(accountId, businessDate, positions.size());
            
            log.info("EOD completed for account {} on {} - {} positions loaded", 
                accountId, businessDate, positions.size());
            
        } catch (DuplicateSnapshotException e) {
            log.warn("Duplicate snapshot detected for account {} on {}: {}", 
                accountId, businessDate, e.getMessage());
            eodRepository.updateStatus(accountId, businessDate, AppConfig.EOD_STATUS_SKIPPED);
            throw e;
            
        } catch (Exception e) {
            log.error("EOD processing failed for account {} on {}", accountId, businessDate, e);
            eodRepository.markFailed(accountId, businessDate, e.getMessage());
            throw new EodProcessingException("EOD failed for account " + accountId, e);
            
        } finally {
            timer.stop(meterRegistry.timer(AppConfig.METRIC_EOD_DURATION, 
                "account", String.valueOf(accountId)));
        }
    }
    
    /**
     * Process late EOD for an account (Phase 4 #21).
     * Used when EOD was missed due to holidays or system issues.
     */
    @Transactional
    public void processLateEod(int accountId, LocalDate businessDate) {
        if (!loaderProperties.featureFlags().lateEodEnabled()) {
            log.warn("Late EOD processing is disabled");
            throw new EodProcessingException("Late EOD processing is disabled");
        }
        
        log.info("Processing late EOD for account {} on {}", accountId, businessDate);
        
        // Check if we're processing a date older than T-1
        LocalDate today = LocalDate.now();
        if (businessDate.isBefore(today.minusDays(5))) {
            throw new EodProcessingException(
                "Cannot process late EOD for dates older than 5 days: " + businessDate);
        }
        
        // Process as normal EOD but with late flag for audit
        processEod(accountId, businessDate);
    }
    
    /**
     * Rollback a batch to the previous version.
     */
    @Transactional
    public void rollbackBatch(int accountId, int batchId) {
        log.warn("Rolling back batch {} for account {}", batchId, accountId);
        
        // Find previous batch
        Integer previousBatchId = batchRepository.findPreviousActiveBatch(accountId, batchId);
        if (previousBatchId == null) {
            throw new EodProcessingException("No previous batch found to rollback to");
        }
        
        // Deactivate current batch
        batchRepository.updateStatus(accountId, batchId, AppConfig.BATCH_STATUS_ROLLED_BACK);
        
        // Reactivate previous batch
        batchRepository.updateStatus(accountId, previousBatchId, AppConfig.BATCH_STATUS_ACTIVE);
        batchRepository.setActivatedAt(accountId, previousBatchId, LocalDateTime.now());
        
        log.info("Rolled back account {} from batch {} to batch {}", 
            accountId, batchId, previousBatchId);
    }
    
    /**
     * Activate a staging batch (blue/green deployment).
     */
    private void activateBatch(int accountId, int newBatchId) {
        // Archive current active batch
        Integer currentActiveBatchId = batchRepository.findActiveBatch(accountId);
        if (currentActiveBatchId != null) {
            batchRepository.updateStatus(accountId, currentActiveBatchId, AppConfig.BATCH_STATUS_ARCHIVED);
            batchRepository.setArchivedAt(accountId, currentActiveBatchId, LocalDateTime.now());
        }
        
        // Activate new batch
        batchRepository.updateStatus(accountId, newBatchId, AppConfig.BATCH_STATUS_ACTIVE);
        batchRepository.setActivatedAt(accountId, newBatchId, LocalDateTime.now());
        
        log.debug("Activated batch {} for account {} (archived batch {})", 
            newBatchId, accountId, currentActiveBatchId);
    }
    
    /**
     * Check for duplicate positions using content hashing (Phase 4 #19).
     */
    private void checkForDuplicates(int accountId, LocalDate businessDate, 
            List<PositionDto> positions) {
        
        String contentHash = duplicateDetectionService.computeHash(positions);
        
        if (duplicateDetectionService.isDuplicate(accountId, businessDate, contentHash)) {
            throw new DuplicateSnapshotException(
                "Duplicate position snapshot detected for account " + accountId + 
                " on " + businessDate);
        }
        
        // Store hash for future comparisons
        duplicateDetectionService.storeHash(accountId, businessDate, contentHash, 
            positions.size());
    }
}
