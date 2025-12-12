package com.vyshali.positionloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-safe configuration properties for the Position Loader service.
 * Maps to YAML properties under the 'loader' prefix.
 */
@ConfigurationProperties(prefix = "loader")
public record LoaderProperties(
        MspmConfig mspm,
        BatchConfig batch,
        KafkaTopics kafka,
        Features features,
        ReconciliationConfig reconciliation
) {

    /**
     * Constructor with null-safe defaults.
     */
    public LoaderProperties {
        if (mspm == null) {
            mspm = new MspmConfig(
                    "https://mspm.ms.com/api",
                    30000,
                    60000,
                    3
            );
        }
        if (batch == null) {
            batch = new BatchConfig(100, 10, 5000);
        }
        if (kafka == null) {
            kafka = new KafkaTopics(
                    "fxan.positions.eod",
                    "fxan.positions.intraday",
                    "fxan.eod.trigger",
                    "fxan.positions.changes",
                    "fxan.positions.dlq"
            );
        }
        if (features == null) {
            features = new Features(false, List.of());
        }
        if (reconciliation == null) {
            reconciliation = new ReconciliationConfig(
                    new BigDecimal("1000000"),
                    new BigDecimal("5"),
                    new BigDecimal("10")
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MSPM CLIENT CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * MSPM (Morgan Stanley Portfolio Management) client configuration.
     */
    public record MspmConfig(
            String baseUrl,
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxRetries
    ) {
        public MspmConfig {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://mspm.ms.com/api";
            }
            if (connectTimeoutMs <= 0) connectTimeoutMs = 30000;
            if (readTimeoutMs <= 0) readTimeoutMs = 60000;
            if (maxRetries <= 0) maxRetries = 3;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH PROCESSING CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Batch processing configuration for EOD and bulk operations.
     */
    public record BatchConfig(
            int size,
            int parallelism,
            int timeoutMs
    ) {
        public BatchConfig {
            if (size <= 0) size = 100;
            if (parallelism <= 0) parallelism = 10;
            if (timeoutMs <= 0) timeoutMs = 5000;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KAFKA TOPIC CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Kafka topic names used by the loader.
     */
    public record KafkaTopics(
            String eodPositions,
            String intradayPositions,
            String eodTrigger,
            String positionChanges,
            String dlq
    ) {
        public KafkaTopics {
            if (eodPositions == null) eodPositions = "fxan.positions.eod";
            if (intradayPositions == null) intradayPositions = "fxan.positions.intraday";
            if (eodTrigger == null) eodTrigger = "fxan.eod.trigger";
            if (positionChanges == null) positionChanges = "fxan.positions.changes";
            if (dlq == null) dlq = "fxan.positions.dlq";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE FLAGS CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Feature flags for gradual rollout and A/B testing.
     */
    public record Features(
            boolean pilotMode,
            List<Integer> pilotAccounts
    ) {
        public Features {
            if (pilotAccounts == null) {
                pilotAccounts = List.of();
            }
        }

        /**
         * Check if an account should be processed based on pilot mode settings.
         */
        public boolean shouldProcessAccount(int accountId) {
            if (!pilotMode) {
                return true; // Not in pilot mode, process all accounts
            }
            return pilotAccounts.contains(accountId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECONCILIATION CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Configuration for position reconciliation checks.
     */
    public record ReconciliationConfig(
            BigDecimal largePositionThreshold,
            BigDecimal quantityTolerancePercent,
            BigDecimal priceTolerancePercent
    ) {
        public ReconciliationConfig {
            if (largePositionThreshold == null) {
                largePositionThreshold = new BigDecimal("1000000");
            }
            if (quantityTolerancePercent == null) {
                quantityTolerancePercent = new BigDecimal("5");
            }
            if (priceTolerancePercent == null) {
                priceTolerancePercent = new BigDecimal("10");
            }
        }
    }
}