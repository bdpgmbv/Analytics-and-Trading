package com.vyshali.common.enums;

/**
 * Types of security identifiers
 */
public enum IdentifierType {
    CUSIP,      // US securities (9 chars)
    ISIN,       // International (12 chars)
    SEDOL,      // UK securities (7 chars)
    TICKER,     // Exchange ticker symbol
    INTERNAL,   // Internal identifiers (for swaps)
    FX,         // FX pair identifier
    CASH;       // Cash currency identifier

    public static IdentifierType fromString(String value) {
        if (value == null) return CUSIP;
        try {
            return IdentifierType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSIP;
        }
    }

    /**
     * Check if this is a standard market identifier
     */
    public boolean isMarketIdentifier() {
        return this == CUSIP || this == ISIN || this == SEDOL;
    }
}
