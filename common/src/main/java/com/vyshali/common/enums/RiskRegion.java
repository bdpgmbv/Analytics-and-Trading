package com.vyshali.common.enums;

/**
 * Risk regions for currency classification
 */
public enum RiskRegion {
    G10("G10 Developed Markets"),
    EM_ASIA("Emerging Markets - Asia"),
    EM_LATAM("Emerging Markets - Latin America"),
    EM_EMEA("Emerging Markets - Europe, Middle East, Africa"),
    EM_AFRICA("Emerging Markets - Africa"),
    OTHER("Other");

    private final String displayName;

    RiskRegion(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RiskRegion fromString(String value) {
        if (value == null) return OTHER;
        try {
            return RiskRegion.valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /**
     * Check if this is a G10 currency region
     */
    public boolean isG10() {
        return this == G10;
    }

    /**
     * Check if this is an emerging market region
     */
    public boolean isEmergingMarket() {
        return this == EM_ASIA || this == EM_LATAM || this == EM_EMEA || this == EM_AFRICA;
    }
}
