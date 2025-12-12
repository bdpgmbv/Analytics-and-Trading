package com.vyshali.common.enums;

/**
 * Sources for prices with priority hierarchy.
 * Priority: OVERRIDE (1) > REALTIME (2) > RCP_SNAP (3) > MSPA (4)
 * Lower number = higher priority.
 */
public enum PriceSource {
    OVERRIDE(1, "User Override"),       // User-provided override (highest priority)
    REALTIME(2, "Real-time Feed"),      // Real-time pricing (20min delayed from Filter)
    RCP_SNAP(3, "RCP Snapshot"),        // EOD snapshot for closed markets
    MSPA(4, "MSPA EOD");                // MSPA end-of-day price (lowest priority)

    private final int priority;
    private final String displayName;

    PriceSource(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PriceSource fromString(String value) {
        if (value == null) return MSPA;
        try {
            return PriceSource.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MSPA;
        }
    }

    public static PriceSource fromPriority(int priority) {
        for (PriceSource source : values()) {
            if (source.priority == priority) {
                return source;
            }
        }
        return MSPA;
    }

    /**
     * Check if this source has higher priority than another
     */
    public boolean hasHigherPriorityThan(PriceSource other) {
        return this.priority < other.priority;
    }

    /**
     * Check if this is real-time pricing
     */
    public boolean isRealtime() {
        return this == REALTIME;
    }

    /**
     * Check if this is a user override
     */
    public boolean isOverride() {
        return this == OVERRIDE;
    }
}
