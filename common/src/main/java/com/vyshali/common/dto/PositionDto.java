package com.vyshali.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Position-related DTOs used across services.
 * Immutable record types for type-safe data transfer.
 */
public record PositionDto(
        Long id,
        int accountId,
        String securityId,
        LocalDate businessDate,
        BigDecimal quantity,
        BigDecimal marketValue,
        BigDecimal costBasis,
        String currency,
        LocalDateTime lastUpdated
) {

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION UPDATE (for real-time/intraday updates)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Represents a position update message from Kafka.
     * Can be a full replace or incremental change.
     */
    public record PositionUpdate(
            int accountId,
            String securityId,
            LocalDate businessDate,
            String updateType,        // QUANTITY_CHANGE, FULL_REPLACE, PRICE_UPDATE
            BigDecimal quantity,      // New quantity (for FULL_REPLACE)
            BigDecimal quantityChange,// Delta quantity (for QUANTITY_CHANGE)
            BigDecimal marketValue,
            BigDecimal costBasis,
            String currency,
            LocalDateTime timestamp
    ) {
        /**
         * Create a full replace update.
         */
        public static PositionUpdate fullReplace(
                int accountId,
                String securityId,
                LocalDate businessDate,
                BigDecimal quantity,
                BigDecimal marketValue,
                BigDecimal costBasis,
                String currency
        ) {
            return new PositionUpdate(
                    accountId, securityId, businessDate,
                    "FULL_REPLACE",
                    quantity, null, marketValue, costBasis, currency,
                    LocalDateTime.now()
            );
        }

        /**
         * Create a quantity change update.
         */
        public static PositionUpdate quantityChange(
                int accountId,
                String securityId,
                LocalDate businessDate,
                BigDecimal quantityChange
        ) {
            return new PositionUpdate(
                    accountId, securityId, businessDate,
                    "QUANTITY_CHANGE",
                    null, quantityChange, null, null, null,
                    LocalDateTime.now()
            );
        }

        /**
         * Create a price update.
         */
        public static PositionUpdate priceUpdate(
                int accountId,
                String securityId,
                LocalDate businessDate,
                BigDecimal marketValue
        ) {
            return new PositionUpdate(
                    accountId, securityId, businessDate,
                    "PRICE_UPDATE",
                    null, null, marketValue, null, null,
                    LocalDateTime.now()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION CHANGE EVENT (published after position modifications)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Event published when a position is created, updated, or deleted.
     * Consumed by other services (Price Service, Hedge Service) for real-time updates.
     */
    public record PositionChangeEvent(
            int accountId,
            String securityId,
            LocalDate businessDate,
            String changeType,        // UPDATE, DELETE, EOD_LOAD, EOD_BATCH
            BigDecimal quantity,
            BigDecimal marketValue,
            LocalDateTime timestamp
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD BATCH (for batch EOD position loading)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Batch of positions for EOD processing.
     * Sent from MSPM integration or uploaded via FTP.
     */
    public record EodBatch(
            int accountId,
            LocalDate businessDate,
            List<PositionDto> positions,
            String source,            // MSPM, FTP, MANUAL
            LocalDateTime receivedAt
    ) {
        /**
         * Create an EOD batch from a list of positions.
         */
        public static EodBatch fromMspm(int accountId, LocalDate businessDate, List<PositionDto> positions) {
            return new EodBatch(accountId, businessDate, positions, "MSPM", LocalDateTime.now());
        }

        /**
         * Create an EOD batch from FTP upload.
         */
        public static EodBatch fromFtp(int accountId, LocalDate businessDate, List<PositionDto> positions) {
            return new EodBatch(accountId, businessDate, positions, "FTP", LocalDateTime.now());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD TRIGGER (to initiate EOD processing)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Message to trigger EOD processing.
     * Can specify specific accounts or trigger for all active accounts.
     */
    public record EodTrigger(
            LocalDate businessDate,
            List<Integer> accountIds,  // null or empty = all active accounts
            String triggeredBy,        // SCHEDULER, MANUAL, MSPM_CALLBACK
            LocalDateTime timestamp
    ) {
        /**
         * Create trigger for all accounts.
         */
        public static EodTrigger forAllAccounts(LocalDate businessDate, String triggeredBy) {
            return new EodTrigger(businessDate, null, triggeredBy, LocalDateTime.now());
        }

        /**
         * Create trigger for specific accounts.
         */
        public static EodTrigger forAccounts(LocalDate businessDate, List<Integer> accountIds, String triggeredBy) {
            return new EodTrigger(businessDate, accountIds, triggeredBy, LocalDateTime.now());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DLQ MESSAGE (for dead letter queue handling)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Wrapper for messages sent to the Dead Letter Queue.
     * Contains original message plus error context for debugging.
     */
    public record DlqMessage(
            String originalTopic,
            String originalKey,
            String originalPayload,
            String errorMessage,
            String errorType,         // parse-error, processing-error, validation-error
            LocalDate businessDate,
            long timestamp
    ) {
        /**
         * Create a DLQ message from a failed processing attempt.
         */
        public static DlqMessage fromError(
                String topic,
                String key,
                String payload,
                Exception error,
                String errorType
        ) {
            return new DlqMessage(
                    topic, key, payload,
                    error.getMessage(),
                    errorType,
                    LocalDate.now(),
                    System.currentTimeMillis()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION SNAPSHOT (for caching/comparison)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lightweight position snapshot for caching and comparison.
     */
    public record PositionSnapshot(
            int accountId,
            String securityId,
            LocalDate businessDate,
            BigDecimal quantity,
            BigDecimal marketValue,
            long snapshotTimestamp
    ) {
        /**
         * Create snapshot from full position DTO.
         */
        public static PositionSnapshot from(PositionDto position) {
            return new PositionSnapshot(
                    position.accountId(),
                    position.securityId(),
                    position.businessDate(),
                    position.quantity(),
                    position.marketValue(),
                    System.currentTimeMillis()
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new PositionDto with updated quantity.
     */
    public PositionDto withQuantity(BigDecimal newQuantity) {
        return new PositionDto(
                this.id, this.accountId, this.securityId, this.businessDate,
                newQuantity, this.marketValue, this.costBasis, this.currency,
                LocalDateTime.now()
        );
    }

    /**
     * Create a new PositionDto with updated market value.
     */
    public PositionDto withMarketValue(BigDecimal newMarketValue) {
        return new PositionDto(
                this.id, this.accountId, this.securityId, this.businessDate,
                this.quantity, newMarketValue, this.costBasis, this.currency,
                LocalDateTime.now()
        );
    }

    /**
     * Create a builder for new positions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PositionDto.
     */
    public static class Builder {
        private Long id;
        private int accountId;
        private String securityId;
        private LocalDate businessDate;
        private BigDecimal quantity;
        private BigDecimal marketValue;
        private BigDecimal costBasis;
        private String currency;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder accountId(int accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder securityId(String securityId) {
            this.securityId = securityId;
            return this;
        }

        public Builder businessDate(LocalDate businessDate) {
            this.businessDate = businessDate;
            return this;
        }

        public Builder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder marketValue(BigDecimal marketValue) {
            this.marketValue = marketValue;
            return this;
        }

        public Builder costBasis(BigDecimal costBasis) {
            this.costBasis = costBasis;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PositionDto build() {
            return new PositionDto(
                    id, accountId, securityId, businessDate,
                    quantity, marketValue, costBasis, currency,
                    LocalDateTime.now()
            );
        }
    }
}