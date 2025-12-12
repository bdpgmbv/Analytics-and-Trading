package com.vyshali.common.enums;

/**
 * Types of transactions
 */
public enum TransactionType {
    BUY,            // Purchase
    SELL,           // Sale
    ROLL_FORWARD;   // FX forward roll

    public static TransactionType fromString(String value) {
        if (value == null) return BUY;
        try {
            return TransactionType.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return BUY;
        }
    }

    /**
     * Check if this is a purchase transaction
     */
    public boolean isPurchase() {
        return this == BUY;
    }

    /**
     * Get the opposite transaction type
     */
    public TransactionType getOpposite() {
        return this == BUY ? SELL : (this == SELL ? BUY : ROLL_FORWARD);
    }
}
