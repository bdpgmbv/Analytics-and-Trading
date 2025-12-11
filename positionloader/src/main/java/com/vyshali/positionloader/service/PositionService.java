package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.Dto;
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
     * Process EOD for an account (today's date).
     */
    @Transactional
    public void processEod(int accountId) {
        processEod(accountId, LocalDate.now());
    }
    
    /**
     * Process EOD for an account on a specific date.
     */
    @Transactional
    public void processEod(int accountId, LocalDate businessDate) {
        log.info("Processing EOD for account {} date {}", accountId, businessDate);
        EodRequest request = EodRequest.of(accountId, businessDate);
        eodProcessingService.processEod(accountId, businessDate);
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
     * Process late EOD for a past date.
     */
    @Transactional
    public void processLateEod(int accountId, LocalDate businessDate) {
        log.warn("Processing late EOD for account {} date {}", accountId, businessDate);
        
        // Validate business date is in the past
        if (businessDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Late EOD can only be processed for past dates");
        }
        
        // Force reprocess even if already completed
        eodProcessingService.reprocessEod(accountId, businessDate);
    }
    
    /**
     * Process intraday JSON message.
     */
    @Transactional
    public void processIntradayJson(String json) {
        log.debug("Processing intraday JSON: {}", json);
        // Implementation depends on JSON format
        // This would typically parse the JSON and update positions
    }
    
    /**
     * Process uploaded positions.
     */
    @Transactional
    public int processUpload(int accountId, List<Dto.Position> positions) {
        log.info("Processing upload of {} positions for account {}", positions.size(), accountId);
        
        // Convert to internal DTO format
        List<PositionDto> positionDtos = positions.stream()
            .map(p -> new PositionDto(
                p.positionId(),
                p.accountId(),
                p.productId(),
                p.businessDate(),
                p.quantity(),
                p.price(),
                p.currency(),
                p.marketValueLocal(),
                p.marketValueBase(),
                BigDecimal.ZERO, // avgCostPrice
                BigDecimal.ZERO, // costLocal
                0, // batchId - will be set
                p.source() != null ? p.source() : "UPLOAD",
                p.positionType() != null ? p.positionType() : "PHYSICAL",
                false // isExcluded
            ))
            .toList();
        
        // Validate
        var validationResult = validationService.validate(positionDtos);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Validation failed: " + validationResult.errors());
        }
        
        LocalDate businessDate = positions.isEmpty() ? LocalDate.now() : 
            positions.get(0).businessDate();
        
        // Create batch and save
        int batchId = dataRepository.createBatch(accountId, businessDate, "UPLOAD");
        int saved = dataRepository.savePositions(positionDtos, batchId);
        dataRepository.completeBatch(batchId, saved);
        
        return saved;
    }
    
    /**
     * Adjust a position manually.
     */
    @Transactional
    public void adjustPosition(int accountId, int productId, BigDecimal quantity, 
            BigDecimal price, String reason, String actor) {
        log.warn("Manual position adjustment by {} for account {} product {}: qty={}, reason={}", 
            actor, accountId, productId, quantity, reason);
        
        LocalDate businessDate = LocalDate.now();
        
        // Log audit
        dataRepository.logAudit("POSITION_ADJUSTMENT", accountId, businessDate,
            String.format("Product %d adjusted to %s by %s: %s", productId, quantity, actor, reason));
        
        // Create adjustment position
        PositionDto adjustment = PositionDto.of(accountId, productId, businessDate, 
            quantity, price != null ? price : BigDecimal.ZERO, "USD")
            .withSource("ADJUSTMENT");
        
        // Save adjustment
        int batchId = dataRepository.createBatch(accountId, businessDate, "ADJUSTMENT");
        dataRepository.savePositions(List.of(adjustment), batchId);
        dataRepository.completeBatch(batchId, 1);
    }
    
    /**
     * Reset EOD status to allow reprocessing.
     */
    @Transactional
    public void resetEodStatus(int accountId, LocalDate businessDate, String actor) {
        log.warn("Resetting EOD status for account {} date {} by {}", accountId, businessDate, actor);
        
        dataRepository.updateEodStatus(accountId, businessDate, "PENDING");
        dataRepository.logAudit("EOD_STATUS_RESET", accountId, businessDate,
            String.format("EOD status reset by %s", actor));
    }
    
    /**
     * Rollback to previous batch.
     */
    @Transactional
    public boolean rollbackEod(int accountId, LocalDate businessDate) {
        log.warn("Rolling back EOD for account {} date {}", accountId, businessDate);
        
        boolean success = dataRepository.batches().rollbackToPrevious(accountId);
        
        if (success) {
            dataRepository.logAudit("EOD_ROLLBACK", accountId, businessDate,
                "Rolled back to previous batch");
        }
        
        return success;
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
