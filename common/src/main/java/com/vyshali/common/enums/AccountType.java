package com.vyshali.common.enums;

/**
 * Types of trading/custody accounts
 */
public enum AccountType {
    CUSTODY,    // Custody account for holding securities
    MARGIN,     // Margin trading account
    DVP,        // Delivery vs Payment account
    PRIME;      // Prime brokerage account

    public static AccountType fromString(String value) {
        if (value == null) return CUSTODY;
        try {
            return AccountType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTODY;
        }
    }
}
