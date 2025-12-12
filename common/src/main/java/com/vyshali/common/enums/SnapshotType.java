package com.vyshali.common.enums;

/**
 * Type of position snapshot
 */
public enum SnapshotType {
    EOD,        // End of Day snapshot
    INTRADAY;   // Intraday snapshot

    public static SnapshotType fromString(String value) {
        if (value == null) return EOD;
        try {
            return SnapshotType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EOD;
        }
    }
}
