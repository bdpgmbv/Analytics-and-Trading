package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - FIXED: Added missing fields (marketValue, currency)
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

/**
 * Position detail from upstream MSPM system.
 * <p>
 * Used by: BulkPositionLoader, SnapshotService, ValidationService
 */
public record PositionDetailDTO(Integer productId, String ticker, String assetClass, String issueCurrency,
                                BigDecimal quantity, String txnType, BigDecimal price, String externalRefId,
                                // NEW FIELDS (were missing, causing compile errors)
                                BigDecimal marketValue, String currency) {
    /**
     * Backward-compatible constructor (8 args) - auto-calculates marketValue
     */
    public PositionDetailDTO(Integer productId, String ticker, String assetClass, String issueCurrency, BigDecimal quantity, String txnType, BigDecimal price, String externalRefId) {
        this(productId, ticker, assetClass, issueCurrency, quantity, txnType, price, externalRefId, calculateMarketValue(quantity, price), issueCurrency  // Default currency to issueCurrency
        );
    }

    /**
     * Helper to calculate market value
     */
    private static BigDecimal calculateMarketValue(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(price);
    }

    /**
     * Builder-style method for creating with explicit market value
     */
    public PositionDetailDTO withMarketValue(BigDecimal mv) {
        return new PositionDetailDTO(this.productId, this.ticker, this.assetClass, this.issueCurrency, this.quantity, this.txnType, this.price, this.externalRefId, mv, this.currency);
    }

    /**
     * Builder-style method for creating with explicit currency
     */
    public PositionDetailDTO withCurrency(String ccy) {
        return new PositionDetailDTO(this.productId, this.ticker, this.assetClass, this.issueCurrency, this.quantity, this.txnType, this.price, this.externalRefId, this.marketValue, ccy);
    }
}