package com.vyshali.positionloader.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Position Data Transfer Object.
 * Internal representation of a position used throughout the application.
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
     * Create a position with basic fields.
     */
    public static PositionDto of(int accountId, int productId, LocalDate businessDate,
            BigDecimal quantity, BigDecimal price, String currency) {
        BigDecimal marketValue = quantity.multiply(price);
        return new PositionDto(
            null,
            accountId,
            productId,
            businessDate,
            quantity,
            price,
            currency,
            marketValue,
            marketValue,
            price,
            marketValue,
            0,
            "MANUAL",
            "PHYSICAL",
            false
        );
    }
    
    /**
     * Create a copy with different source.
     */
    public PositionDto withSource(String newSource) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase,
            avgCostPrice, costLocal,
            batchId, newSource, positionType, isExcluded
        );
    }
    
    /**
     * Create a copy with different batch ID.
     */
    public PositionDto withBatchId(int newBatchId) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase,
            avgCostPrice, costLocal,
            newBatchId, source, positionType, isExcluded
        );
    }
    
    /**
     * Create a copy with exclusion flag.
     */
    public PositionDto withExcluded(boolean excluded) {
        return new PositionDto(
            positionId, accountId, productId, businessDate,
            quantity, price, currency,
            marketValueLocal, marketValueBase,
            avgCostPrice, costLocal,
            batchId, source, positionType, excluded
        );
    }
    
    /**
     * Check if position has zero quantity.
     */
    public boolean isZeroQuantity() {
        return quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * Check if position is valid (has required fields).
     */
    public boolean isValid() {
        return accountId > 0 
            && productId > 0 
            && businessDate != null 
            && quantity != null 
            && currency != null && !currency.isBlank();
    }
}
