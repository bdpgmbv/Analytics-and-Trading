package com.vyshali.positionloader.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Main configuration class for the Position Loader service.
 * Provides type-safe access to configuration properties and helper methods.
 */
@Configuration
@EnableConfigurationProperties(LoaderProperties.class)
@RequiredArgsConstructor
public class LoaderConfig {

    private final LoaderProperties properties;

    // ═══════════════════════════════════════════════════════════════════════════
    // MSPM CLIENT CONFIGURATION ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get MSPM configuration.
     */
    public Mspm mspm() {
        return new Mspm(properties.mspm());
    }

    public static class Mspm {
        private final LoaderProperties.MspmConfig config;

        Mspm(LoaderProperties.MspmConfig config) {
            this.config = config;
        }

        public String baseUrl() {
            return config.baseUrl();
        }

        public int connectTimeoutMs() {
            return config.connectTimeoutMs();
        }

        public int readTimeoutMs() {
            return config.readTimeoutMs();
        }

        public int maxRetries() {
            return config.maxRetries();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH PROCESSING CONFIGURATION ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get batch processing configuration.
     */
    public Batch batch() {
        return new Batch(properties.batch());
    }

    public static class Batch {
        private final LoaderProperties.BatchConfig config;

        Batch(LoaderProperties.BatchConfig config) {
            this.config = config;
        }

        public int size() {
            return config.size();
        }

        public int parallelism() {
            return config.parallelism();
        }

        public int timeoutMs() {
            return config.timeoutMs();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KAFKA TOPIC CONFIGURATION ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get Kafka topic configuration.
     */
    public Kafka kafka() {
        return new Kafka(properties.kafka());
    }

    public static class Kafka {
        private final LoaderProperties.KafkaTopics config;

        Kafka(LoaderProperties.KafkaTopics config) {
            this.config = config;
        }

        public String eodPositions() {
            return config.eodPositions();
        }

        public String intradayPositions() {
            return config.intradayPositions();
        }

        public String eodTrigger() {
            return config.eodTrigger();
        }

        public String positionChanges() {
            return config.positionChanges();
        }

        public String dlq() {
            return config.dlq();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE FLAGS CONFIGURATION ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get feature flags configuration.
     */
    public Features features() {
        return new Features(properties.features());
    }

    public static class Features {
        private final LoaderProperties.Features config;

        Features(LoaderProperties.Features config) {
            this.config = config;
        }

        public boolean pilotMode() {
            return config.pilotMode();
        }

        public List<Integer> pilotAccounts() {
            return config.pilotAccounts();
        }

        /**
         * Check if an account should be processed based on pilot mode settings.
         */
        public boolean shouldProcessAccount(int accountId) {
            return config.shouldProcessAccount(accountId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECONCILIATION CONFIGURATION ACCESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get reconciliation configuration.
     */
    public Reconciliation reconciliation() {
        return new Reconciliation(properties.reconciliation());
    }

    public static class Reconciliation {
        private final LoaderProperties.ReconciliationConfig config;

        Reconciliation(LoaderProperties.ReconciliationConfig config) {
            this.config = config;
        }

        /**
         * Threshold for flagging large positions (in base currency).
         * Positions above this value get extra scrutiny.
         */
        public BigDecimal largePositionThreshold() {
            return config.largePositionThreshold();
        }

        /**
         * Tolerance percentage for quantity mismatches.
         * E.g., 5 means 5% tolerance.
         */
        public BigDecimal quantityTolerancePercent() {
            return config.quantityTolerancePercent();
        }

        /**
         * Tolerance percentage for price mismatches.
         * E.g., 10 means 10% tolerance.
         */
        public BigDecimal priceTolerancePercent() {
            return config.priceTolerancePercent();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIRECT PROPERTY ACCESS (for simple cases)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get raw properties (for cases where typed access isn't needed).
     */
    public LoaderProperties getProperties() {
        return properties;
    }
}