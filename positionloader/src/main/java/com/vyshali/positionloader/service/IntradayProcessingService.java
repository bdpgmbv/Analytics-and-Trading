package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.dto.TradeFillDto;
import com.vyshali.positionloader.repository.PositionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for processing intraday position updates from trade fills.
 */
@Service
public class IntradayProcessingService {
    
    private static final Logger log = LoggerFactory.getLogger(IntradayProcessingService.class);
    
    private final PositionRepository positionRepository;
    private final LoaderConfig config;
    private final Counter fillsProcessed;
    private final Counter fillsSkipped;
    
    // Cache of latest positions by account for quick updates
    private final Map<Integer, List<PositionDto>> positionCache = new ConcurrentHashMap<>();
    
    public IntradayProcessingService(
            PositionRepository positionRepository,
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.positionRepository = positionRepository;
        this.config = config;
        this.fillsProcessed = Counter.builder("intraday.fills.processed")
            .description("Trade fills processed")
            .register(meterRegistry);
        this.fillsSkipped = Counter.builder("intraday.fills.skipped")
            .description("Trade fills skipped")
            .register(meterRegistry);
    }
    
    /**
     * Process a trade fill and update position.
     */
    @Transactional
    public ProcessResult processTradeFill(TradeFillDto fill, int accountId) {
        if (!config.features().intradayProcessingEnabled()) {
            log.debug("Intraday processing disabled, skipping fill {}", fill.fillId());
            fillsSkipped.increment();
            return ProcessResult.skipped("Intraday processing disabled");
        }
        
        log.debug("Processing trade fill {} for account {}", fill.fillId(), accountId);
        
        try {
            LocalDate businessDate = LocalDate.now();
            
            // Get current positions
            List<PositionDto> positions = getPositions(accountId, businessDate);
            
            // Find position for this product (by symbol lookup - simplified)
            // In real implementation, would need symbol-to-productId mapping
            int productId = resolveProductId(fill.symbol());
            
            PositionDto existingPosition = positions.stream()
                .filter(p -> p.productId() == productId)
                .findFirst()
                .orElse(null);
            
            if (existingPosition != null) {
                // Update existing position
                BigDecimal newQuantity = existingPosition.quantity()
                    .add(BigDecimal.valueOf(fill.signedQuantity()));
                
                PositionDto updatedPosition = new PositionDto(
                    existingPosition.positionId(),
                    existingPosition.accountId(),
                    existingPosition.productId(),
                    existingPosition.businessDate(),
                    newQuantity,
                    BigDecimal.valueOf(fill.price()),  // Update to latest price
                    existingPosition.currency(),
                    newQuantity.multiply(BigDecimal.valueOf(fill.price())),
                    newQuantity.multiply(BigDecimal.valueOf(fill.price())),
                    existingPosition.avgCostPrice(),
                    existingPosition.costLocal(),
                    existingPosition.batchId(),
                    "INTRADAY",
                    existingPosition.positionType(),
                    existingPosition.isExcluded()
                );
                
                // In a real implementation, would update the position in DB
                log.info("Updated position for account {} product {}: qty {} -> {}", 
                    accountId, productId, existingPosition.quantity(), newQuantity);
                
            } else {
                // Create new position
                PositionDto newPosition = PositionDto.of(
                    accountId, productId, businessDate,
                    BigDecimal.valueOf(fill.signedQuantity()),
                    BigDecimal.valueOf(fill.price()),
                    "USD"
                ).withSource("INTRADAY");
                
                log.info("Created new position for account {} product {}: qty {}", 
                    accountId, productId, fill.signedQuantity());
            }
            
            // Invalidate cache
            positionCache.remove(accountId);
            
            fillsProcessed.increment();
            return ProcessResult.success(fill.fillId());
            
        } catch (Exception e) {
            log.error("Failed to process trade fill {} for account {}", 
                fill.fillId(), accountId, e);
            return ProcessResult.failed(fill.fillId(), e.getMessage());
        }
    }
    
    /**
     * Process multiple trade fills.
     */
    @Transactional
    public List<ProcessResult> processTradeFills(List<TradeFillDto> fills, int accountId) {
        return fills.stream()
            .map(fill -> processTradeFill(fill, accountId))
            .toList();
    }
    
    /**
     * Get positions from cache or database.
     */
    private List<PositionDto> getPositions(int accountId, LocalDate businessDate) {
        return positionCache.computeIfAbsent(accountId, 
            id -> positionRepository.findByAccountAndDate(id, businessDate));
    }
    
    /**
     * Resolve symbol to product ID.
     * Simplified - in real implementation would use reference data service.
     */
    private int resolveProductId(String symbol) {
        // Simplified hash-based ID for demo
        return Math.abs(symbol.hashCode() % 100000);
    }
    
    /**
     * Clear position cache for account.
     */
    public void invalidateCache(int accountId) {
        positionCache.remove(accountId);
    }
    
    /**
     * Clear all position cache.
     */
    public void clearCache() {
        positionCache.clear();
    }
    
    /**
     * Processing result.
     */
    public record ProcessResult(
        String fillId,
        Status status,
        String message
    ) {
        public enum Status {
            SUCCESS, SKIPPED, FAILED
        }
        
        public static ProcessResult success(String fillId) {
            return new ProcessResult(fillId, Status.SUCCESS, null);
        }
        
        public static ProcessResult skipped(String reason) {
            return new ProcessResult(null, Status.SKIPPED, reason);
        }
        
        public static ProcessResult failed(String fillId, String error) {
            return new ProcessResult(fillId, Status.FAILED, error);
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
