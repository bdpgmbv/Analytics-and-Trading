package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 12:48 PM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Position detail from upstream MSPM system.
 */
public record PositionDTO(@NotNull Integer productId, String ticker, String assetClass, String currency,
                          @NotNull BigDecimal quantity, BigDecimal price, BigDecimal marketValue, String txnType,
                          String externalRefId) {
    // Compact constructor with defaults
    public PositionDTO {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (price == null) price = BigDecimal.ZERO;
        if (marketValue == null) marketValue = quantity.multiply(price);
        if (currency == null) currency = "USD";
    }

    // Simple constructor for common case
    public PositionDTO(Integer productId, String ticker, BigDecimal quantity, BigDecimal price) {
        this(productId, ticker, "EQUITY", "USD", quantity, price, null, "BUY", null);
    }

    public boolean hasZeroPrice() {
        return price == null || price.signum() == 0;
    }

    public boolean isValid() {
        return productId != null && quantity != null && !hasZeroPrice();
    }
}
