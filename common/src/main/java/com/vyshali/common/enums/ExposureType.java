package com.vyshali.common.enums;

/**
 * Types of currency exposure weights
 */
public enum ExposureType {
    GENERIC,    // Generic currency exposure (based on issue currency)
    SPECIFIC;   // Specific currency exposure (user-defined breakdown)

    public static ExposureType fromString(String value) {
        if (value == null) return GENERIC;
        try {
            return ExposureType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERIC;
        }
    }
}
