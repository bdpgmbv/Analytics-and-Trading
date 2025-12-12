package com.vyshali.positionloader.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Position Data Transfer Object.
 * 
 * This is the MAIN position record used throughout the Position Loader service.
 * Contains all fields needed for position storage, retrieval, and processing.
 */
public record PositionDto(
    Long positionId,
    int accountId,
    int productId,
    LocalDate businessDate,
    BigDecimal quantity,
    BigDecimal price,
    String currency,
    BigDecimal marketValueLocal,
    BigDecimal marketValueBase,
    BigDecimal avgCostPrice,
    BigDecimal costLocal,
    int batchId,
    String source,
    String positionType,
    boolean isExcluded
) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Create a basic position with minimal required fields.
     */
    public static PositionDto of(int accountId, int productId, LocalDate businessDate,
                                  BigDecimal quantity, BigDecimal price, String currency) {
        BigDecimal marketValue = quantity != null && price != null 
            ? quantity.multiply(price) 
            : BigDecimal.ZERO;
        
        return new PositionDto(
            null,                    // positionId - auto-generated
            accountId,
            productId,
            businessDate,
            quantity != null ? quantity : BigDecimal.ZERO,
            price != null ? price : BigDecimal.ZERO,
            currency != null ? currency : "USD",
            marketValue,             // marketValueLocal
            marketValue,             // marketValueBase (same if no FX conversion)
            price,                   // avgCostPrice
            marketValue,             // costLocal
            0,                       // batchId - set later
            "SYSTEM",                // source - default
            "PHYSICAL",              // positionType - default
            false                    // isExcluded
        );
    }
    
    /**
     * Create position from update event.
     */
    public static PositionDto fromUpdate(PositionUpdate update, LocalDate businessDate) {
        BigDecimal marketValue = update.quantity() != null && update.price() != null
            ? update.quantity().multiply(update.price())
            : BigDecimal.ZERO;
        
        return new PositionDto(
            null,
            update.accountId(),
            update.productId(),
            businessDate,
            update.quantity(),
            update.price(),
            update.currency() != null ? update.currency() : "USD",
            update.marketValueLocal() != null ? update.marketValueLocal() : marketValue,
            update.marketValueBase() != null ? update.marketValueBase() : marketValue,
            update.price(),
            marketValue,
            0,
            update.source() != null ? update.source() : "KAFKA",
            "PHYSICAL",
            false
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // BUILDER/WITH METHODS (for immutable updates)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Create copy with different source.
     */
    public PositionDto withSource(String newSource) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase, avgCostPrice, costLocal,
            batchId, newSource, positionType, isExcluded
        );
    }
    
    /**
     * Create copy with different batch ID.
     */
    public PositionDto withBatchId(int newBatchId) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase, avgCostPrice, costLocal,
            newBatchId, source, positionType, isExcluded
        );
    }
    
    /**
     * Create copy with different quantity.
     */
    public PositionDto withQuantity(BigDecimal newQuantity) {
        BigDecimal newMarketValue = newQuantity != null && price != null
            ? newQuantity.multiply(price)
            : BigDecimal.ZERO;
        
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            newQuantity, price, currency,
            newMarketValue, newMarketValue, avgCostPrice, newMarketValue,
            batchId, source, positionType, isExcluded
        );
    }
    
    /**
     * Create copy with different price and recalculated market value.
     */
    public PositionDto withPrice(BigDecimal newPrice) {
        BigDecimal newMarketValue = quantity != null && newPrice != null
            ? quantity.multiply(newPrice)
            : BigDecimal.ZERO;
        
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, newPrice, currency,
            newMarketValue, newMarketValue, avgCostPrice, costLocal,
            batchId, source, positionType, isExcluded
        );
    }
    
    /**
     * Create copy with position ID set.
     */
    public PositionDto withPositionId(Long newPositionId) {
        return new PositionDto(
            newPositionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase, avgCostPrice, costLocal,
            batchId, source, positionType, isExcluded
        );
    }
    
    /**
     * Create copy marked as excluded.
     */
    public PositionDto withExcluded(boolean excluded) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase, avgCostPrice, costLocal,
            batchId, source, positionType, excluded
        );
    }
    
    /**
     * Create copy with different position type.
     */
    public PositionDto withPositionType(String newPositionType) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase, avgCostPrice, costLocal,
            batchId, source, newPositionType, isExcluded
        );
    }
    
    /**
     * Create copy with market values in base currency.
     */
    public PositionDto withBaseMarketValue(BigDecimal baseMarketValue) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, baseMarketValue, avgCostPrice, costLocal,
            batchId, source, positionType, isExcluded
        );
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Check if position has valid quantity.
     */
    public boolean hasQuantity() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) != 0;
    }
    
    /**
     * Check if position has valid price.
     */
    public boolean hasPrice() {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get absolute quantity.
     */
    public BigDecimal absQuantity() {
        return quantity != null ? quantity.abs() : BigDecimal.ZERO;
    }
    
    /**
     * Check if this is a long position.
     */
    public boolean isLong() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a short position.
     */
    public boolean isShort() {
        return quantity != null && quantity.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Calculate unrealized P&L (simplified).
     */
    public BigDecimal unrealizedPnl() {
        if (quantity == null || price == null || avgCostPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(price.subtract(avgCostPrice));
    }
    
    /**
     * Get unique key for deduplication.
     */
    public String uniqueKey() {
        return accountId + "-" + productId + "-" + businessDate;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // NESTED DTOs FOR KAFKA MESSAGES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Position update from Kafka (intraday updates).
     */
    public record PositionUpdate(
        int accountId,
        int productId,
        String ticker,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal marketValueLocal,
        BigDecimal marketValueBase,
        String currency,
        LocalDate businessDate,
        String source
    ) {
        public static PositionUpdate of(int accountId, int productId, 
                                        BigDecimal quantity, BigDecimal price) {
            return new PositionUpdate(
                accountId, productId, null, quantity, price,
                null, null, "USD", LocalDate.now(), "KAFKA"
            );
        }
    }
    
    /**
     * Position snapshot for publishing to downstream services.
     */
    public record PositionSnapshot(
        int accountId,
        int productId,
        String ticker,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal averageCost,
        BigDecimal marketValueLocal,
        BigDecimal marketValueBase,
        BigDecimal unrealizedPnl,
        String currency,
        String positionType,
        boolean isExcluded,
        LocalDateTime updatedAt
    ) {
        /**
         * Create snapshot from PositionDto.
         */
        public static PositionSnapshot from(PositionDto dto, String ticker) {
            return new PositionSnapshot(
                dto.accountId(),
                dto.productId(),
                ticker,
                dto.quantity(),
                dto.price(),
                dto.avgCostPrice(),
                dto.marketValueLocal(),
                dto.marketValueBase(),
                dto.unrealizedPnl(),
                dto.currency(),
                dto.positionType(),
                dto.isExcluded(),
                LocalDateTime.now()
            );
        }
    }
    
    /**
     * Batch information for tracking.
     */
    public record BatchInfo(
        long batchId,
        int accountId,
        String status,
        LocalDate businessDate,
        String source,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
    ) {}
    
    /**
     * EOD run status.
     */
    public record EodStatus(
        LocalDate businessDate,
        String status,
        int accountsProcessed,
        int accountsFailed,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage
    ) {}
}
