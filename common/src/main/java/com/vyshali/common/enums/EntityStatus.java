package com.vyshali.common.enums;

/**
 * Status for clients, funds, accounts
 */
public enum EntityStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED;

    public static EntityStatus fromString(String value) {
        if (value == null) return ACTIVE;
        try {
            return EntityStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
