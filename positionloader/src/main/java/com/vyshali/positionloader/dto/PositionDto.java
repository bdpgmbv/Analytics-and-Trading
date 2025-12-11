package com.vyshali.positionloader.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Position Data Transfer Object.
 * Used for transferring position data between layers.
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
    
    /**
     * Compact constructor with defaults.
     */
    public PositionDto {
        if (currency == null) currency = "USD";
        if (source == null) source = "MSPM";
        if (positionType == null) positionType = "PHYSICAL";
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (price == null) price = BigDecimal.ZERO;
        if (marketValueLocal == null) marketValueLocal = BigDecimal.ZERO;
        if (marketValueBase == null) marketValueBase = BigDecimal.ZERO;
        if (avgCostPrice == null) avgCostPrice = BigDecimal.ZERO;
        if (costLocal == null) costLocal = BigDecimal.ZERO;
    }
    
    /**
     * Simplified constructor for common use cases.
     */
    public static PositionDto of(int accountId, int productId, LocalDate businessDate,
            BigDecimal quantity, BigDecimal price, String currency) {
        return new PositionDto(
            null,           // positionId
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            quantity.multiply(price),  // marketValueLocal
            quantity.multiply(price),  // marketValueBase (same if USD)
            BigDecimal.ZERO,           // avgCostPrice
            BigDecimal.ZERO,           // costLocal
            0,                         // batchId
            "MSPM",                    // source
            "PHYSICAL",                // positionType
            false                      // isExcluded
        );
    }
    
    /**
     * Create a copy with different source.
     */
    public PositionDto withSource(String newSource) {
        return new PositionDto(
            positionId,
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            marketValueLocal,
            marketValueBase,
            avgCostPrice,
            costLocal,
            batchId,
            newSource,
            positionType,
            isExcluded
        );
    }
    
    /**
     * Create a copy with different batch ID.
     */
    public PositionDto withBatchId(int newBatchId) {
        return new PositionDto(
            positionId,
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            marketValueLocal,
            marketValueBase,
            avgCostPrice,
            costLocal,
            newBatchId,
            source,
            positionType,
            isExcluded
        );
    }
    
    /**
     * Calculate market value.
     */
    public BigDecimal calculateMarketValue() {
        return quantity.multiply(price);
    }
}
