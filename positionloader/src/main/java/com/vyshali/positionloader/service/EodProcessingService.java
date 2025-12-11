package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.BatchRepository;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.repository.EodRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for EOD processing operations.
 */
@Service
public class EodProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(EodProcessingService.class);
    
    private final DataRepository dataRepository;
    private final EodRepository eodRepository;
    private final BatchRepository batchRepository;
    private final AuditRepository auditRepository;
    private final MspmClientService mspmClient;
    private final PositionValidationService validationService;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;
    
    public EodProcessingService(
            DataRepository dataRepository,
            EodRepository eodRepository,
            BatchRepository batchRepository,
            AuditRepository auditRepository,
            MspmClientService mspmClient,
            PositionValidationService validationService,
            LoaderConfig config,
            AlertService alertService,
            MeterRegistry meterRegistry) {
        this.dataRepository = dataRepository;
        this.eodRepository = eodRepository;
        this.batchRepository = batchRepository;
        this.auditRepository = auditRepository;
        this.mspmClient = mspmClient;
        this.validationService = validationService;
        this.config = config;
        this.alertService = alertService;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Process EOD for an account.
     */
    @Transactional
    public EodResult processEod(int accountId, LocalDate businessDate) {
        log.info("Starting EOD processing for account {} date {}", accountId, businessDate);
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            // Check if already complete
            if (eodRepository.isComplete(accountId, businessDate)) {
                log.info("EOD already complete for account {} date {}", accountId, businessDate);
                return EodResult.alreadyComplete(accountId, businessDate);
            }
            
            // Check if account is disabled
            if (config.features().isAccountDisabled(accountId)) {
                log.warn("Account {} is disabled, skipping EOD", accountId);
                return EodResult.skipped(accountId, businessDate, "Account disabled");
            }
            
            // Check pilot mode
            if (!config.features().shouldProcessAccount(accountId)) {
                log.info("Account {} not in pilot mode, skipping", accountId);
                return EodResult.skipped(accountId, businessDate, "Not in pilot mode");
            }
            
            // Update status to processing
            eodRepository.createOrUpdate(accountId, businessDate, AppConfig.EOD_STATUS_PROCESSING);
            
            // Fetch positions from MSPM
            List<PositionDto> positions = mspmClient.fetchPositions(accountId, businessDate);
            
            if (positions.isEmpty()) {
                log.warn("No positions returned from MSPM for account {} date {}", 
                    accountId, businessDate);
                eodRepository.updateStatus(accountId, businessDate, AppConfig.EOD_STATUS_NO_DATA);
                return EodResult.noData(accountId, businessDate);
            }
            
            // Validate positions
            if (config.features().validationEnabled()) {
                var validationResult = validationService.validate(positions);
                if (!validationResult.isValid()) {
                    log.error("Validation failed for account {} date {}: {}", 
                        accountId, businessDate, validationResult.errors());
                    eodRepository.markFailed(accountId, businessDate, 
                        "Validation failed: " + validationResult.errors());
                    return EodResult.validationFailed(accountId, businessDate, validationResult.errors());
                }
            }
            
            // Check for duplicates
            if (config.features().duplicateDetectionEnabled()) {
                // Implementation would check for duplicate positions
            }
            
            // Create batch and save positions
            int batchId = batchRepository.createBatch(accountId, businessDate, AppConfig.SOURCE_EOD);
            
            try {
                int saved = dataRepository.savePositions(positions, batchId);
                batchRepository.completeBatch(batchId, saved);
                
                // Mark EOD complete
                eodRepository.markCompleted(accountId, businessDate, saved);
                
                // Log audit
                auditRepository.log("EOD_COMPLETE", accountId, businessDate,
                    String.format("Processed %d positions", saved));
                
                timer.stop(meterRegistry.timer("eod.processing.time", "status", "success"));
                meterRegistry.counter("eod.processing.success").increment();
                
                log.info("EOD complete for account {} date {}: {} positions", 
                    accountId, businessDate, saved);
                
                return EodResult.success(accountId, businessDate, batchId, saved);
                
            } catch (Exception e) {
                batchRepository.failBatch(batchId, e.getMessage());
                throw e;
            }
            
        } catch (Exception e) {
            log.error("EOD processing failed for account {} date {}", 
                accountId, businessDate, e);
            
            eodRepository.markFailed(accountId, businessDate, e.getMessage());
            alertService.eodFailed(accountId, e.getMessage());
            
            timer.stop(meterRegistry.timer("eod.processing.time", "status", "failed"));
            meterRegistry.counter("eod.processing.failed").increment();
            
            return EodResult.failed(accountId, businessDate, e.getMessage());
        }
    }
    
    /**
     * Reprocess EOD (force even if already complete).
     */
    @Transactional
    public EodResult reprocessEod(int accountId, LocalDate businessDate) {
        log.warn("Reprocessing EOD for account {} date {}", accountId, businessDate);
        
        // Reset status first
        eodRepository.resetStatus(accountId, businessDate);
        
        // Delete existing positions
        dataRepository.deletePositions(accountId, businessDate);
        
        // Process again
        return processEod(accountId, businessDate);
    }
    
    /**
     * Check if EOD is complete.
     */
    public boolean isEodComplete(int accountId, LocalDate businessDate) {
        return eodRepository.isComplete(accountId, businessDate);
    }
    
    /**
     * Get EOD status.
     */
    public String getEodStatus(int accountId, LocalDate businessDate) {
        return eodRepository.getStatus(accountId, businessDate);
    }
    
    /**
     * EOD processing result.
     */
    public record EodResult(
        int accountId,
        LocalDate businessDate,
        Status status,
        int batchId,
        int positionCount,
        String message,
        List<String> errors
    ) {
        public enum Status {
            SUCCESS, ALREADY_COMPLETE, NO_DATA, SKIPPED, VALIDATION_FAILED, FAILED
        }
        
        public static EodResult success(int accountId, LocalDate date, int batchId, int count) {
            return new EodResult(accountId, date, Status.SUCCESS, batchId, count, null, List.of());
        }
        
        public static EodResult alreadyComplete(int accountId, LocalDate date) {
            return new EodResult(accountId, date, Status.ALREADY_COMPLETE, -1, 0, 
                "Already completed", List.of());
        }
        
        public static EodResult noData(int accountId, LocalDate date) {
            return new EodResult(accountId, date, Status.NO_DATA, -1, 0, 
                "No data from MSPM", List.of());
        }
        
        public static EodResult skipped(int accountId, LocalDate date, String reason) {
            return new EodResult(accountId, date, Status.SKIPPED, -1, 0, reason, List.of());
        }
        
        public static EodResult validationFailed(int accountId, LocalDate date, List<String> errors) {
            return new EodResult(accountId, date, Status.VALIDATION_FAILED, -1, 0, 
                "Validation failed", errors);
        }
        
        public static EodResult failed(int accountId, LocalDate date, String error) {
            return new EodResult(accountId, date, Status.FAILED, -1, 0, error, List.of(error));
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
