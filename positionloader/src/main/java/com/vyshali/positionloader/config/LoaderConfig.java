package com.vyshali.positionloader.config;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Collections;

/**
 * Loader configuration that provides typed access to loader properties.
 * This bridges the gap between LoaderProperties (external config) and 
 * application code that needs typed configuration access.
 */
@Component
public class LoaderConfig {
    
    private final LoaderProperties properties;
    private final Features features;
    private final Dlq dlq;
    private final Processing processing;
    private final Validation validation;
    
    public LoaderConfig(LoaderProperties properties) {
        this.properties = properties;
        this.features = new Features(properties.featureFlags());
        this.dlq = new Dlq(properties.dlqRetentionDays(), properties.dlqMaxRetries());
        this.processing = new Processing(properties.processingThreads(), properties.batchSize());
        this.validation = new Validation(properties.validation());
    }
    
    /**
     * Number of parallel processing threads.
     */
    public int parallelThreads() {
        return properties.processingThreads();
    }
    
    /**
     * Maximum retries for DLQ messages.
     */
    public int dlqMaxRetries() {
        return properties.dlqMaxRetries();
    }
    
    /**
     * Batch size for processing.
     */
    public int batchSize() {
        return properties.batchSize();
    }
    
    /**
     * Feature flags access.
     */
    public Features features() {
        return features;
    }
    
    /**
     * DLQ configuration access.
     */
    public Dlq dlq() {
        return dlq;
    }
    
    /**
     * Processing configuration access.
     */
    public Processing processing() {
        return processing;
    }
    
    /**
     * Validation configuration access.
     */
    public Validation validation() {
        return validation;
    }
    
    /**
     * Get the underlying properties.
     */
    public LoaderProperties properties() {
        return properties;
    }
    
    /**
     * Feature flags configuration.
     */
    public static class Features {
        private final LoaderProperties.FeatureFlagsConfig flags;
        
        Features(LoaderProperties.FeatureFlagsConfig flags) {
            this.flags = flags != null ? flags : new LoaderProperties.FeatureFlagsConfig(
                true, true, true, true, true, true, Collections.emptyList(), Collections.emptyList());
        }
        
        public boolean eodProcessingEnabled() {
            return flags.eodProcessingEnabled();
        }
        
        public boolean intradayProcessingEnabled() {
            return flags.intradayProcessingEnabled();
        }
        
        public boolean validationEnabled() {
            return flags.validationEnabled();
        }
        
        public boolean duplicateDetectionEnabled() {
            return flags.duplicateDetectionEnabled();
        }
        
        public boolean reconciliationEnabled() {
            return flags.reconciliationEnabled();
        }
        
        public boolean archivalEnabled() {
            return flags.archivalEnabled();
        }
        
        public List<Integer> disabledAccounts() {
            return flags.disabledAccounts() != null ? flags.disabledAccounts() : Collections.emptyList();
        }
        
        public List<Integer> pilotAccounts() {
            return flags.pilotAccounts() != null ? flags.pilotAccounts() : Collections.emptyList();
        }
        
        /**
         * Check if an account is disabled.
         */
        public boolean isAccountDisabled(int accountId) {
            return disabledAccounts().contains(accountId);
        }
        
        /**
         * Check if account should be processed (pilot mode check).
         */
        public boolean shouldProcessAccount(int accountId) {
            // If disabled, never process
            if (isAccountDisabled(accountId)) {
                return false;
            }
            // If pilot accounts are defined, only process those
            List<Integer> pilots = pilotAccounts();
            if (pilots != null && !pilots.isEmpty()) {
                return pilots.contains(accountId);
            }
            // Otherwise process all non-disabled accounts
            return true;
        }
    }
    
    /**
     * DLQ configuration.
     */
    public static class Dlq {
        private final int retentionDays;
        private final int maxRetries;
        
        Dlq(int retentionDays, int maxRetries) {
            this.retentionDays = retentionDays > 0 ? retentionDays : 7;
            this.maxRetries = maxRetries > 0 ? maxRetries : 3;
        }
        
        public int retentionDays() {
            return retentionDays;
        }
        
        public int maxRetries() {
            return maxRetries;
        }
    }
    
    /**
     * Processing configuration.
     */
    public static class Processing {
        private final int threads;
        private final int batchSize;
        
        Processing(int threads, int batchSize) {
            this.threads = threads > 0 ? threads : 4;
            this.batchSize = batchSize > 0 ? batchSize : 1000;
        }
        
        public int threads() {
            return threads;
        }
        
        public int batchSize() {
            return batchSize;
        }
    }
    
    /**
     * Validation configuration.
     */
    public static class Validation {
        private final boolean enabled;
        private final boolean rejectZeroQuantity;
        private final int maxPriceThreshold;
        
        Validation(LoaderProperties.ValidationConfig config) {
            if (config != null) {
                this.enabled = config.enabled();
                this.rejectZeroQuantity = config.rejectZeroQuantity();
                this.maxPriceThreshold = config.maxPriceThreshold();
            } else {
                this.enabled = true;
                this.rejectZeroQuantity = true;
                this.maxPriceThreshold = 1000000;
            }
        }
        
        public boolean enabled() {
            return enabled;
        }
        
        public boolean rejectZeroQuantity() {
            return rejectZeroQuantity;
        }
        
        public int maxPriceThreshold() {
            return maxPriceThreshold;
        }
    }
}
