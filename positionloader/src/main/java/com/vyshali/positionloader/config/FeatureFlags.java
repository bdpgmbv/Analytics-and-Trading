package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - 1:04 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized feature flags and settings.
 * Bind to 'app' prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class FeatureFlags {

    private Eod eod = new Eod();
    private Validation validation = new Validation();
    private Cache cache = new Cache();

    // ==================== EOD SETTINGS ====================

    public static class Eod {
        private int maxConcurrency = 50;
        private int timeoutMinutes = 30;
        private boolean retryFailed = true;

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getTimeoutMinutes() {
            return timeoutMinutes;
        }

        public void setTimeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
        }

        public boolean isRetryFailed() {
            return retryFailed;
        }

        public void setRetryFailed(boolean retryFailed) {
            this.retryFailed = retryFailed;
        }
    }

    // ==================== VALIDATION SETTINGS ====================

    public static class Validation {
        private boolean strictMode = false;
        private double zeroPriceThresholdPct = 10.0;
        private double suspiciousChangePct = 50.0;

        public boolean isStrictMode() {
            return strictMode;
        }

        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }

        public double getZeroPriceThresholdPct() {
            return zeroPriceThresholdPct;
        }

        public void setZeroPriceThresholdPct(double zeroPriceThresholdPct) {
            this.zeroPriceThresholdPct = zeroPriceThresholdPct;
        }

        public double getSuspiciousChangePct() {
            return suspiciousChangePct;
        }

        public void setSuspiciousChangePct(double suspiciousChangePct) {
            this.suspiciousChangePct = suspiciousChangePct;
        }
    }

    // ==================== CACHE SETTINGS ====================

    public static class Cache {
        private boolean enabled = true;
        private int positionTtlHours = 24;
        private int snapshotTtlHours = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPositionTtlHours() {
            return positionTtlHours;
        }

        public void setPositionTtlHours(int positionTtlHours) {
            this.positionTtlHours = positionTtlHours;
        }

        public int getSnapshotTtlHours() {
            return snapshotTtlHours;
        }

        public void setSnapshotTtlHours(int snapshotTtlHours) {
            this.snapshotTtlHours = snapshotTtlHours;
        }
    }

    // ==================== GETTERS/SETTERS ====================

    public Eod getEod() {
        return eod;
    }

    public void setEod(Eod eod) {
        this.eod = eod;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }
}
