package com.vyshali.common.enums;

/**
 * Asset classes for products/securities
 */
public enum AssetClass {
    EQUITY,         // Stocks
    EQUITY_SWAP,    // Equity total return swaps
    FX_FORWARD,     // FX forward contracts
    FX_SPOT,        // FX spot transactions
    BOND,           // Fixed income
    CASH;           // Cash/currency

    public static AssetClass fromString(String value) {
        if (value == null) return EQUITY;
        try {
            return AssetClass.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EQUITY;
        }
    }

    /**
     * Check if this asset class is an FX product
     */
    public boolean isFxProduct() {
        return this == FX_FORWARD || this == FX_SPOT;
    }

    /**
     * Check if this asset class is a derivative
     */
    public boolean isDerivative() {
        return this == EQUITY_SWAP || this == FX_FORWARD;
    }
}
