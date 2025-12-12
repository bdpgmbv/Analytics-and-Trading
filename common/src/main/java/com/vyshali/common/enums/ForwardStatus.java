package com.vyshali.common.enums;

/**
 * Status of FX forward contracts
 */
public enum ForwardStatus {
    ACTIVE,     // Active forward contract
    MATURED,    // Contract has matured/settled
    ROLLED,     // Contract has been rolled to new date
    CANCELLED;  // Contract cancelled

    public static ForwardStatus fromString(String value) {
        if (value == null) return ACTIVE;
        try {
            return ForwardStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }

    /**
     * Check if forward is still active
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Check if forward is in terminal state
     */
    public boolean isTerminal() {
        return this == MATURED || this == CANCELLED;
    }
}
