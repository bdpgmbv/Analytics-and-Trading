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
                          @NotNull BigDecimal quantity, BigDecimal price, String txnType, String externalRefId) {
    public PositionDTO {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (price == null) price = BigDecimal.ZERO;
        if (currency == null) currency = "USD";
        if (assetClass == null) assetClass = "EQUITY";
    }

    public BigDecimal marketValue() {
        return quantity.multiply(price);
    }

    public boolean hasZeroPrice() {
        return price == null || price.signum() == 0;
    }
}