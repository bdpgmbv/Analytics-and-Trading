package com.vyshali.common.enums;

/**
 * Status of a position snapshot
 */
public enum SnapshotStatus {
    ACTIVE,      // Current active snapshot
    INACTIVE,    // Manually deactivated
    SUPERSEDED;  // Replaced by a newer snapshot

    public static SnapshotStatus fromString(String value) {
        if (value == null) return ACTIVE;
        try {
            return SnapshotStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
