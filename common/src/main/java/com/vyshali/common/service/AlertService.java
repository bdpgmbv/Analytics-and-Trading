package com.vyshali.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized alert service for publishing operational alerts.
 * Supports alert throttling to prevent spam.
 *
 * Usage patterns:
 * - alertService.critical("circuit-breaker-open", "Redis circuit breaker opened", "redis-primary")
 * - alertService.warning("high-latency", "Database queries exceeding threshold")
 * - alertService.eodFailed(accountId, "Processing timeout")
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    // Alert throttling - prevent same alert within this period (milliseconds)
    private static final long THROTTLE_PERIOD_MS = 60_000; // 1 minute
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON ALERT TYPES (use these constants for consistency)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final String ALERT_DLQ_THRESHOLD = "dlq-threshold-exceeded";
    public static final String ALERT_RATE_LIMIT = "rate-limit-hit";
    public static final String ALERT_HIGH_LAG = "high-consumer-lag";
    public static final String ALERT_CIRCUIT_OPEN = "circuit-breaker-open";
    public static final String ALERT_CIRCUIT_CLOSED = "circuit-breaker-closed";
    public static final String ALERT_VALIDATION_FAILED = "validation-failed";
    public static final String ALERT_RECONCILIATION_MISMATCH = "reconciliation-mismatch";
    public static final String ALERT_SERVICE_UNAVAILABLE = "service-unavailable";
    public static final String ALERT_FIX_CONNECTION_LOST = "fix-connection-lost";
    public static final String ALERT_FIX_CONNECTION_RESTORED = "fix-connection-restored";
    public static final String ALERT_ORPHANED_ORDERS = "orphaned-orders-detected";
    public static final String ALERT_DATABASE_ISSUE = "database-connection-issue";
    public static final String ALERT_MEMORY_PRESSURE = "memory-pressure";
    public static final String ALERT_EOD_FAILED = "eod-processing-failed";
    public static final String ALERT_EOD_DELAYED = "eod-processing-delayed";
    public static final String ALERT_POSITION_MISMATCH = "position-mismatch";
    public static final String ALERT_PRICE_STALE = "stale-price-detected";

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY METHODS (3 parameters - alertType, message, context)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send an INFO level alert.
     * @param alertType Type/category of alert (use constants above)
     * @param message Human-readable description
     * @param context Additional context (e.g., account ID, service name)
     */
    public void info(String alertType, String message, String context) {
        if (shouldThrottle(alertType + "-info")) {
            return;
        }
        log.info("[ALERT:INFO] type={}, message={}, context={}", alertType, message, context);
        publishAlert("INFO", alertType, message, context);
    }

    /**
     * Send a WARNING level alert.
     * @param alertType Type/category of alert (use constants above)
     * @param message Human-readable description
     * @param context Additional context (e.g., account ID, service name)
     */
    public void warning(String alertType, String message, String context) {
        if (shouldThrottle(alertType + "-warning")) {
            return;
        }
        log.warn("[ALERT:WARNING] type={}, message={}, context={}", alertType, message, context);
        publishAlert("WARNING", alertType, message, context);
    }

    /**
     * Send a CRITICAL level alert.
     * @param alertType Type/category of alert (use constants above)
     * @param message Human-readable description
     * @param context Additional context (e.g., account ID, service name)
     */
    public void critical(String alertType, String message, String context) {
        // Critical alerts are never throttled
        log.error("[ALERT:CRITICAL] type={}, message={}, context={}", alertType, message, context);
        publishAlert("CRITICAL", alertType, message, context);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE OVERLOADS (2 parameters - for simpler usage)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send an INFO alert without context.
     */
    public void info(String alertType, String message) {
        info(alertType, message, null);
    }

    /**
     * Send a WARNING alert without context.
     */
    public void warning(String alertType, String message) {
        warning(alertType, message, null);
    }

    /**
     * Alias for warning() - some callers use warn().
     */
    public void warn(String alertType, String message) {
        warning(alertType, message, null);
    }

    /**
     * Alias for warning() with context.
     */
    public void warn(String alertType, String message, String context) {
        warning(alertType, message, context);
    }

    /**
     * Send a CRITICAL alert without context.
     */
    public void critical(String alertType, String message) {
        critical(alertType, message, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN-SPECIFIC ALERT METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Alert for EOD processing failure by account.
     * @param accountId The account that failed
     * @param error Error description
     */
    public void eodFailed(int accountId, String error) {
        critical(ALERT_EOD_FAILED,
                String.format("EOD processing failed for account %d: %s", accountId, error),
                "accountId=" + accountId);
    }

    /**
     * Alert for EOD processing failure by date.
     * @param businessDate The business date that failed
     * @param error Error description
     */
    public void eodFailed(LocalDate businessDate, String error) {
        critical(ALERT_EOD_FAILED,
                String.format("EOD processing failed for date %s: %s", businessDate, error),
                "businessDate=" + businessDate);
    }

    /**
     * Alert for DLQ threshold exceeded.
     * @param topic Kafka topic name
     * @param count Number of messages in DLQ
     * @param threshold Configured threshold
     */
    public void dlqThresholdExceeded(String topic, long count, long threshold) {
        critical(ALERT_DLQ_THRESHOLD,
                String.format("DLQ message count %d exceeds threshold %d", count, threshold),
                "topic=" + topic);
    }

    /**
     * Alert for circuit breaker state change.
     * @param serviceName Name of the service
     * @param isOpen True if circuit opened, false if closed
     */
    public void circuitBreakerStateChange(String serviceName, boolean isOpen) {
        if (isOpen) {
            critical(ALERT_CIRCUIT_OPEN,
                    String.format("Circuit breaker opened for service: %s", serviceName),
                    "service=" + serviceName);
        } else {
            info(ALERT_CIRCUIT_CLOSED,
                    String.format("Circuit breaker closed for service: %s", serviceName),
                    "service=" + serviceName);
        }
    }

    /**
     * Alert for high consumer lag.
     * @param consumerGroup Kafka consumer group
     * @param lag Current lag value
     * @param threshold Configured threshold
     */
    public void highConsumerLag(String consumerGroup, long lag, long threshold) {
        warning(ALERT_HIGH_LAG,
                String.format("Consumer lag %d exceeds threshold %d", lag, threshold),
                "consumerGroup=" + consumerGroup);
    }

    /**
     * Alert for position reconciliation mismatch.
     * @param accountId Account with mismatch
     * @param details Mismatch details
     */
    public void reconciliationMismatch(int accountId, String details) {
        warning(ALERT_RECONCILIATION_MISMATCH,
                String.format("Position mismatch for account %d: %s", accountId, details),
                "accountId=" + accountId);
    }

    /**
     * Alert for stale prices.
     * @param securityId Security with stale price
     * @param lastUpdateMinutes Minutes since last update
     */
    public void stalePriceDetected(String securityId, long lastUpdateMinutes) {
        warning(ALERT_PRICE_STALE,
                String.format("Price for %s is %d minutes old", securityId, lastUpdateMinutes),
                "securityId=" + securityId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if alert should be throttled.
     * @param alertKey Unique key for the alert
     * @return true if alert should be suppressed
     */
    private boolean shouldThrottle(String alertKey) {
        long now = System.currentTimeMillis();
        Long lastTime = lastAlertTime.get(alertKey);

        if (lastTime != null && (now - lastTime) < THROTTLE_PERIOD_MS) {
            log.debug("Throttling alert: {}", alertKey);
            return true;
        }

        lastAlertTime.put(alertKey, now);
        return false;
    }

    /**
     * Publish alert to external systems (Kafka, PagerDuty, etc.)
     * Override this in production to integrate with actual alerting systems.
     */
    protected void publishAlert(String severity, String alertType, String message, String context) {
        // In production, this would:
        // 1. Publish to Kafka alert topic
        // 2. Send to PagerDuty for critical alerts
        // 3. Update metrics/dashboards
        // 4. Send to Slack/Teams channels

        // For now, just log (actual implementation would be injected)
        log.debug("Publishing alert: severity={}, type={}, message={}, context={}",
                severity, alertType, message, context);
    }

    /**
     * Clear throttle cache (useful for testing).
     */
    public void clearThrottleCache() {
        lastAlertTime.clear();
    }
}