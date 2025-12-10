package com.vyshali.positionloader.exception;

/*
 * 12/10/2025 - 11:49 AM
 * @author Vyshali Prabananth Lal
 */

/*
 * 12/10/2025 - NEW: Custom exception with error codes for Position Loader
 * @author Vyshali Prabananth Lal
 *
 * Replaces generic RuntimeException throughout the codebase.
 * Provides structured error information for monitoring and debugging.
 */

import java.util.Map;
import java.util.HashMap;

/**
 * Domain-specific exception for Position Loader operations.
 * <p>
 * Usage:
 * throw new PositionLoaderException(ErrorCode.MSPM_UNAVAILABLE, "Connection refused");
 * throw PositionLoaderException.zeroPriceDetected(accountId, count);
 */
public class PositionLoaderException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    /**
     * Error codes for categorizing failures
     */
    public enum ErrorCode {
        // MSPM Integration Errors (1xx)
        MSPM_UNAVAILABLE(101, "MSPM service unavailable", true), MSPM_TIMEOUT(102, "MSPM request timed out", true), MSPM_INVALID_RESPONSE(103, "Invalid response from MSPM", false), MSPM_AUTHENTICATION_FAILED(104, "MSPM authentication failed", false),

        // Validation Errors (2xx)
        VALIDATION_FAILED(201, "Position validation failed", false), ZERO_PRICE_DETECTED(202, "Zero price detected in positions", false), INVALID_QUANTITY(203, "Invalid quantity value", false), MISSING_PRODUCT(204, "Product not found in reference data", false), DUPLICATE_POSITION(205, "Duplicate position detected", false),

        // Database Errors (3xx)
        DB_SAVE_FAILED(301, "Database save operation failed", true), DB_CONNECTION_FAILED(302, "Database connection failed", true), DB_CONSTRAINT_VIOLATION(303, "Database constraint violated", false), DB_DEADLOCK(304, "Database deadlock detected", true),

        // Processing Errors (4xx)
        TIMEOUT(401, "Operation timed out", true), EOD_DEADLINE_MISSED(402, "EOD processing deadline missed", false), BATCH_PROCESSING_FAILED(403, "Batch processing failed", true), IDEMPOTENCY_VIOLATION(404, "Duplicate transaction detected", false),

        // Kafka/Messaging Errors (5xx)
        KAFKA_PUBLISH_FAILED(501, "Failed to publish to Kafka", true), KAFKA_CONSUME_FAILED(502, "Failed to consume from Kafka", true), DLQ_PROCESSING_FAILED(503, "DLQ replay failed", true),

        // Cache Errors (6xx)
        CACHE_MISS(601, "Cache miss - data not available", false), CACHE_WRITE_FAILED(602, "Failed to write to cache", false),

        // Configuration Errors (9xx)
        CONFIG_ERROR(901, "Configuration error", false), UNKNOWN_ERROR(999, "Unknown error occurred", true);

        private final int code;
        private final String description;
        private final boolean retryable;

        ErrorCode(int code, String description, boolean retryable) {
            this.code = code;
            this.description = description;
            this.retryable = retryable;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    // ============================================================
    // CONSTRUCTORS
    // ============================================================

    public PositionLoaderException(ErrorCode errorCode, String message) {
        super(formatMessage(errorCode, message));
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    public PositionLoaderException(ErrorCode errorCode, String message, Throwable cause) {
        super(formatMessage(errorCode, message), cause);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    public PositionLoaderException(ErrorCode errorCode, String message, Map<String, Object> context) {
        super(formatMessage(errorCode, message));
        this.errorCode = errorCode;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
    }

    public PositionLoaderException(ErrorCode errorCode, String message, Map<String, Object> context, Throwable cause) {
        super(formatMessage(errorCode, message), cause);
        this.errorCode = errorCode;
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
    }

    // ============================================================
    // FACTORY METHODS (Convenience)
    // ============================================================

    public static PositionLoaderException mspmUnavailable(String details) {
        return new PositionLoaderException(ErrorCode.MSPM_UNAVAILABLE, details);
    }

    public static PositionLoaderException mspmTimeout(Integer accountId) {
        return new PositionLoaderException(ErrorCode.MSPM_TIMEOUT, "Timeout fetching account " + accountId, Map.of("accountId", accountId));
    }

    public static PositionLoaderException zeroPriceDetected(Integer accountId, int count) {
        return new PositionLoaderException(ErrorCode.ZERO_PRICE_DETECTED, String.format("Found %d positions with zero price for account %d", count, accountId), Map.of("accountId", accountId, "zeroPriceCount", count));
    }

    public static PositionLoaderException validationFailed(Integer accountId, String reason) {
        return new PositionLoaderException(ErrorCode.VALIDATION_FAILED, reason, Map.of("accountId", accountId));
    }

    public static PositionLoaderException dbSaveFailed(Integer accountId, Throwable cause) {
        return new PositionLoaderException(ErrorCode.DB_SAVE_FAILED, "Failed to save positions for account " + accountId, Map.of("accountId", accountId), cause);
    }

    public static PositionLoaderException eodDeadlineMissed(String runId, int pendingAccounts) {
        return new PositionLoaderException(ErrorCode.EOD_DEADLINE_MISSED, String.format("EOD run %s missed deadline with %d accounts pending", runId, pendingAccounts), Map.of("runId", runId, "pendingAccounts", pendingAccounts));
    }

    public static PositionLoaderException idempotencyViolation(String transactionId) {
        return new PositionLoaderException(ErrorCode.IDEMPOTENCY_VIOLATION, "Duplicate transaction: " + transactionId, Map.of("transactionId", transactionId));
    }

    public static PositionLoaderException batchFailed(Integer accountId, int batchId, Throwable cause) {
        return new PositionLoaderException(ErrorCode.BATCH_PROCESSING_FAILED, String.format("Batch %d failed for account %d", batchId, accountId), Map.of("accountId", accountId, "batchId", batchId), cause);
    }

    // ============================================================
    // ACCESSORS
    // ============================================================

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }

    public PositionLoaderException withContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private static String formatMessage(ErrorCode code, String message) {
        return String.format("[%s-%d] %s: %s", code.name(), code.getCode(), code.getDescription(), message);
    }

    /**
     * Get a loggable summary of this exception
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PositionLoaderException[");
        sb.append("code=").append(errorCode.name());
        sb.append(", retryable=").append(errorCode.isRetryable());
        if (!context.isEmpty()) {
            sb.append(", context=").append(context);
        }
        sb.append(", message=").append(getMessage());
        sb.append("]");
        return sb.toString();
    }
}
