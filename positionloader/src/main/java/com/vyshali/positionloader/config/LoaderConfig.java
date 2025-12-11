package com.vyshali.positionloader.config;

import org.springframework.stereotype.Component;

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
    
    public LoaderConfig(LoaderProperties properties) {
        this.properties = properties;
        this.features = new Features(properties.featureFlags());
        this.dlq = new Dlq(properties.dlqRetentionDays());
        this.processing = new Processing(properties.processingThreads(), properties.batchSize());
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
        return 3; // Default, could be added to LoaderProperties
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
     * Get the underlying properties.
     */
    public LoaderProperties properties() {
        return properties;
    }
    
    /**
     * Feature flags configuration.
     */
    public static class Features {
        private final LoaderProperties.FeatureFlags flags;
        
        Features(LoaderProperties.FeatureFlags flags) {
            this.flags = flags != null ? flags : new LoaderProperties.FeatureFlags(
                true, true, false, false, false);
        }
        
        public boolean eodProcessingEnabled() {
            return flags.eodProcessing();
        }
        
        public boolean intradayProcessingEnabled() {
            return flags.intradayProcessing();
        }
        
        public boolean duplicateDetectionEnabled() {
            return flags.duplicateDetection();
        }
        
        public boolean archivalEnabled() {
            return flags.archival();
        }
        
        public boolean auditEnabled() {
            return flags.audit();
        }
    }
    
    /**
     * DLQ configuration.
     */
    public static class Dlq {
        private final int retentionDays;
        private final int maxRetries;
        
        Dlq(int retentionDays) {
            this.retentionDays = retentionDays;
            this.maxRetries = 3;
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
            this.threads = threads;
            this.batchSize = batchSize;
        }
        
        public int threads() {
            return threads;
        }
        
        public int batchSize() {
            return batchSize;
        }
    }
}
