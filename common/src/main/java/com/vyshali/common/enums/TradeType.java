package com.vyshali.common.enums;

/**
 * Types of FX trades
 */
public enum TradeType {
    SPOT,       // FX Spot trade (T+2 settlement)
    FORWARD,    // FX Forward trade
    SWAP;       // FX Swap (spot + forward)

    public static TradeType fromString(String value) {
        if (value == null) return SPOT;
        try {
            return TradeType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SPOT;
        }
    }

    /**
     * Check if this trade has forward component
     */
    public boolean hasForwardLeg() {
        return this == FORWARD || this == SWAP;
    }
}
