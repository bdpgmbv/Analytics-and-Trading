package com.vyshali.common.enums;

/**
 * Status of trade executions sent to FX Matrix.
 * This enum addresses Issue #2: No tracking for executed trades.
 */
public enum ExecutionStatus {
    SENT,       // Trade sent to FX Matrix, awaiting confirmation
    EXECUTED,   // Trade successfully executed
    REJECTED,   // Trade rejected by FX Matrix
    FAILED,     // Trade failed due to system error
    CANCELLED;  // Trade cancelled before execution

    public static ExecutionStatus fromString(String value) {
        if (value == null) return SENT;
        try {
            return ExecutionStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SENT;
        }
    }

    /**
     * Check if trade is in terminal state (no more updates expected)
     */
    public boolean isTerminal() {
        return this == EXECUTED || this == REJECTED || this == FAILED || this == CANCELLED;
    }

    /**
     * Check if trade was successful
     */
    public boolean isSuccessful() {
        return this == EXECUTED;
    }

    /**
     * Check if trade needs attention/intervention
     */
    public boolean needsAttention() {
        return this == REJECTED || this == FAILED;
    }
}
