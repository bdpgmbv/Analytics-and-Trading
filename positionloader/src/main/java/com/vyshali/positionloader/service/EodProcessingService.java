package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.exception.EodProcessingException;
import com.vyshali.positionloader.repository.BatchRepository;
import com.vyshali.positionloader.repository.EodRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for End-of-Day position processing.
 */
@Service
public class EodProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(EodProcessingService.class);
    
    private final PositionRepository positionRepository;
    private final BatchRepository batchRepository;
    private final EodRepository eodRepository;
    private final MspmClientService mspmClient;
    private final PositionValidationService validationService;
    private final DuplicateDetectionService duplicateDetection;
    private final LoaderConfig config;
    private final ExecutorService executor;
    private final Timer eodTimer;
    
    public EodProcessingService(
            PositionRepository positionRepository,
            BatchRepository batchRepository,
            EodRepository eodRepository,
            MspmClientService mspmClient,
            PositionValidationService validationService,
            DuplicateDetectionService duplicateDetection,
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.batchRepository = batchRepository;
        this.eodRepository = eodRepository;
        this.mspmClient = mspmClient;
        this.validationService = validationService;
        this.duplicateDetection = duplicateDetection;
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.parallelThreads());
        this.eodTimer = Timer.builder("eod.processing.time")
            .description("EOD processing time")
            .register(meterRegistry);
    }
    
    /**
     * Process EOD for an account.
     */
    @Transactional
    public EodResult processEod(int accountId, LocalDate businessDate) {
        log.info("Starting EOD processing for account {} date {}", accountId, businessDate);
        
        return eodTimer.record(() -> {
            try {
                // Check if already processed
                if (eodRepository.isComplete(accountId, businessDate)) {
                    log.info("EOD already complete for account {} date {}", accountId, businessDate);
                    return EodResult.alreadyProcessed(accountId, businessDate);
                }
                
                // Update status to processing
                eodRepository.updateStatus(accountId, businessDate, "PROCESSING");
                
                // Fetch positions from MSPM
                List<PositionDto> positions = mspmClient.fetchPositions(accountId, businessDate);
                
                if (positions.isEmpty()) {
                    log.warn("No positions returned from MSPM for account {} date {}", 
                        accountId, businessDate);
                    eodRepository.updateStatus(accountId, businessDate, "NO_DATA");
                    return EodResult.noData(accountId, businessDate);
                }
                
                // Check for duplicates if enabled
                if (config.features().duplicateDetectionEnabled()) {
                    if (duplicateDetection.isDuplicate(accountId, businessDate, positions)) {
                        log.info("Duplicate snapshot detected for account {} date {}", 
                            accountId, businessDate);
                        eodRepository.updateStatus(accountId, businessDate, "DUPLICATE");
                        return EodResult.duplicate(accountId, businessDate);
                    }
                }
                
                // Validate positions
                var validationResult = validationService.validate(positions);
                if (!validationResult.isValid()) {
                    log.error("Validation failed for account {} date {}: {}", 
                        accountId, businessDate, validationResult.errors());
                    eodRepository.updateStatus(accountId, businessDate, "VALIDATION_FAILED");
                    return EodResult.validationFailed(accountId, businessDate, validationResult.errors());
                }
                
                // Create batch
                int batchId = batchRepository.createBatch(accountId, businessDate, "EOD");
                
                // Delete existing positions for this date
                positionRepository.deleteByAccountAndDate(accountId, businessDate);
                
                // Insert new positions
                int inserted = positionRepository.batchInsert(positions, batchId);
                
                // Complete batch
                batchRepository.completeBatch(batchId, inserted);
                
                // Store hash for duplicate detection
                if (config.features().duplicateDetectionEnabled()) {
                    duplicateDetection.storeHash(accountId, businessDate, positions);
                }
                
                // Update EOD status
                eodRepository.updateStatus(accountId, businessDate, "COMPLETE");
                
                log.info("EOD complete for account {} date {}: {} positions", 
                    accountId, businessDate, inserted);
                
                return EodResult.success(accountId, businessDate, batchId, inserted);
                
            } catch (Exception e) {
                log.error("EOD processing failed for account {} date {}", 
                    accountId, businessDate, e);
                eodRepository.updateStatus(accountId, businessDate, "FAILED");
                throw new EodProcessingException(
                    "EOD processing failed: " + e.getMessage(), accountId, businessDate, e);
            }
        });
    }
    
    /**
     * Process EOD asynchronously.
     */
    public CompletableFuture<EodResult> processEodAsync(int accountId, LocalDate businessDate) {
        return CompletableFuture.supplyAsync(
            () -> processEod(accountId, businessDate), executor);
    }
    
    /**
     * Reprocess EOD (force).
     */
    @Transactional
    public EodResult reprocessEod(int accountId, LocalDate businessDate) {
        log.info("Reprocessing EOD for account {} date {}", accountId, businessDate);
        
        // Reset status
        eodRepository.updateStatus(accountId, businessDate, "PENDING");
        
        // Clear duplicate hash if exists
        if (config.features().duplicateDetectionEnabled()) {
            duplicateDetection.clearHash(accountId, businessDate);
        }
        
        return processEod(accountId, businessDate);
    }
    
    /**
     * Check if EOD is complete for account.
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
        List<String> errors
    ) {
        public enum Status {
            SUCCESS, ALREADY_PROCESSED, NO_DATA, DUPLICATE, VALIDATION_FAILED, FAILED
        }
        
        public static EodResult success(int accountId, LocalDate date, int batchId, int count) {
            return new EodResult(accountId, date, Status.SUCCESS, batchId, count, List.of());
        }
        
        public static EodResult alreadyProcessed(int accountId, LocalDate date) {
            return new EodResult(accountId, date, Status.ALREADY_PROCESSED, -1, 0, List.of());
        }
        
        public static EodResult noData(int accountId, LocalDate date) {
            return new EodResult(accountId, date, Status.NO_DATA, -1, 0, List.of());
        }
        
        public static EodResult duplicate(int accountId, LocalDate date) {
            return new EodResult(accountId, date, Status.DUPLICATE, -1, 0, List.of());
        }
        
        public static EodResult validationFailed(int accountId, LocalDate date, List<String> errors) {
            return new EodResult(accountId, date, Status.VALIDATION_FAILED, -1, 0, errors);
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
