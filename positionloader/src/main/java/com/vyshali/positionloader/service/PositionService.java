package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.EodRequest;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.DataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main service for position operations.
 * Orchestrates position loading, retrieval, and management.
 */
@Service
public class PositionService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    
    private final DataRepository dataRepository;
    private final EodProcessingService eodProcessingService;
    private final PositionValidationService validationService;
    private final MeterRegistry meterRegistry;
    
    public PositionService(
            DataRepository dataRepository,
            EodProcessingService eodProcessingService,
            PositionValidationService validationService,
            MeterRegistry meterRegistry) {
        this.dataRepository = dataRepository;
        this.eodProcessingService = eodProcessingService;
        this.validationService = validationService;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Get positions for account and date.
     */
    public List<PositionDto> getPositions(int accountId, LocalDate businessDate) {
        log.debug("Getting positions for account {} date {}", accountId, businessDate);
        return dataRepository.findPositions(accountId, businessDate);
    }
    
    /**
     * Get latest positions for account.
     */
    public List<PositionDto> getLatestPositions(int accountId) {
        log.debug("Getting latest positions for account {}", accountId);
        return dataRepository.findLatestPositions(accountId);
    }
    
    /**
     * Process EOD request.
     */
    @Transactional
    public EodProcessingService.EodResult processEod(EodRequest request) {
        log.info("Processing EOD request for account {} date {}", 
            request.accountId(), request.businessDate());
        
        if (request.forceReprocess()) {
            return eodProcessingService.reprocessEod(
                request.accountId(), request.businessDate());
        }
        
        return eodProcessingService.processEod(
            request.accountId(), request.businessDate());
    }
    
    /**
     * Save positions for account.
     */
    @Transactional
    public SaveResult savePositions(int accountId, LocalDate businessDate, 
            List<PositionDto> positions, String source) {
        log.info("Saving {} positions for account {} date {}", 
            positions.size(), accountId, businessDate);
        
        // Validate
        var validationResult = validationService.validate(positions);
        if (!validationResult.isValid()) {
            log.warn("Validation failed for account {} date {}: {}", 
                accountId, businessDate, validationResult.errors());
            return SaveResult.validationFailed(validationResult.errors());
        }
        
        // Create batch
        int batchId = dataRepository.createBatch(accountId, businessDate, source);
        
        try {
            // Delete existing positions
            int deleted = dataRepository.deletePositions(accountId, businessDate);
            if (deleted > 0) {
                log.info("Deleted {} existing positions for account {} date {}", 
                    deleted, accountId, businessDate);
            }
            
            // Insert new positions
            int inserted = dataRepository.savePositions(positions, batchId);
            
            // Complete batch
            dataRepository.completeBatch(batchId, inserted);
            
            // Log audit
            dataRepository.logAudit("POSITIONS_SAVED", accountId, businessDate,
                String.format("Saved %d positions from %s", inserted, source));
            
            log.info("Saved {} positions for account {} date {} (batch {})", 
                inserted, accountId, businessDate, batchId);
            
            return SaveResult.success(batchId, inserted);
            
        } catch (Exception e) {
            log.error("Failed to save positions for account {} date {}", 
                accountId, businessDate, e);
            dataRepository.failBatch(batchId, e.getMessage());
            return SaveResult.failed(e.getMessage());
        }
    }
    
    /**
     * Delete positions for account and date.
     */
    @Transactional
    public int deletePositions(int accountId, LocalDate businessDate) {
        log.info("Deleting positions for account {} date {}", accountId, businessDate);
        
        int deleted = dataRepository.deletePositions(accountId, businessDate);
        
        dataRepository.logAudit("POSITIONS_DELETED", accountId, businessDate,
            String.format("Deleted %d positions", deleted));
        
        return deleted;
    }
    
    /**
     * Get position count for account and date.
     */
    public int getPositionCount(int accountId, LocalDate businessDate) {
        return dataRepository.positions().countByAccountAndDate(accountId, businessDate);
    }
    
    /**
     * Get total market value for account and date.
     */
    public BigDecimal getTotalMarketValue(int accountId, LocalDate businessDate) {
        return dataRepository.positions().getTotalMarketValue(accountId, businessDate);
    }
    
    /**
     * Get positions grouped by product type.
     */
    public Map<String, List<PositionDto>> getPositionsByType(int accountId, LocalDate businessDate) {
        List<PositionDto> positions = getPositions(accountId, businessDate);
        return positions.stream()
            .collect(Collectors.groupingBy(PositionDto::positionType));
    }
    
    /**
     * Check if EOD is complete.
     */
    public boolean isEodComplete(int accountId, LocalDate businessDate) {
        return eodProcessingService.isEodComplete(accountId, businessDate);
    }
    
    /**
     * Get EOD status.
     */
    public String getEodStatus(int accountId, LocalDate businessDate) {
        return eodProcessingService.getEodStatus(accountId, businessDate);
    }
    
    /**
     * Result of save operation.
     */
    public record SaveResult(
        Status status,
        int batchId,
        int positionCount,
        List<String> errors
    ) {
        public enum Status {
            SUCCESS, VALIDATION_FAILED, FAILED
        }
        
        public static SaveResult success(int batchId, int count) {
            return new SaveResult(Status.SUCCESS, batchId, count, List.of());
        }
        
        public static SaveResult validationFailed(List<String> errors) {
            return new SaveResult(Status.VALIDATION_FAILED, -1, 0, errors);
        }
        
        public static SaveResult failed(String error) {
            return new SaveResult(Status.FAILED, -1, 0, List.of(error));
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
