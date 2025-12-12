package com.vyshali.common.enums;

/**
 * Source systems for position data
 */
public enum SourceSystem {
    MSPM,       // MS Position Management
    MSPA,       // MS Prime Analytics
    MANUAL,     // Manual upload via UI
    FTP;        // FTP file upload

    public static SourceSystem fromString(String value) {
        if (value == null) return MSPM;
        try {
            return SourceSystem.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MSPM;
        }
    }

    /**
     * Check if this is an automated source
     */
    public boolean isAutomated() {
        return this == MSPM || this == MSPA;
    }

    /**
     * Check if this is a manual source
     */
    public boolean isManual() {
        return this == MANUAL || this == FTP;
    }
}
