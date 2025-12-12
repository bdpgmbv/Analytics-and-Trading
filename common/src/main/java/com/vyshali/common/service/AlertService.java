package com.vyshali.common.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing alerts and notifications.
 * Provides consistent alerting across all services with metrics integration.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final MeterRegistry meterRegistry;
    private final Counter alertsRaised;
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    // Throttle alerts to avoid spam (minimum seconds between same alerts)
    private static final long ALERT_THROTTLE_SECONDS = 60;

    public AlertService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.alertsRaised = Counter.builder("alerts.raised.total")
                .description("Total alerts raised")
                .register(meterRegistry);
    }

    /**
     * Raise an info-level alert.
     */
    public void info(String alertType, String message, String context) {
        log.info("ALERT [{}] {}: {}", alertType, context, message);
        meterRegistry.counter("alerts.info", "type", alertType).increment();
    }

    /**
     * Raise a warning-level alert.
     */
    public void warning(String alertType, String message, String context) {
        if (shouldThrottle(alertType)) {
            log.debug("Alert throttled: {} - {}", alertType, message);
            return;
        }

        log.warn("ALERT [{}] {}: {}", alertType, context, message);
        meterRegistry.counter("alerts.warning", "type", alertType).increment();
        alertsRaised.increment();
        recordAlertTime(alertType);
    }

    /**
     * Raise a critical-level alert.
     */
    public void critical(String alertType, String message, String context) {
        // Critical alerts are never throttled
        log.error("CRITICAL ALERT [{}] {}: {}", alertType, context, message);
        meterRegistry.counter("alerts.critical", "type", alertType).increment();
        alertsRaised.increment();
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMMON ALERT TYPES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Alert when DLQ threshold is exceeded.
     */
    public void dlqThresholdExceeded(int currentDepth, int threshold) {
        warning("DLQ_THRESHOLD_EXCEEDED",
                String.format("DLQ depth (%d) exceeds threshold (%d)", currentDepth, threshold),
                "DLQ");
    }

    /**
     * Alert when rate limit is hit.
     */
    public void rateLimitHit(String source, int rejectedCount) {
        warning("RATE_LIMIT_HIT",
                String.format("Rate limit hit for %s, %d rejections", source, rejectedCount),
                source);
    }

    /**
     * Alert when consumer lag is high.
     */
    public void highConsumerLag(String topic, String group, long lag) {
        warning("HIGH_CONSUMER_LAG",
                String.format("Consumer lag for %s/%s: %d messages", topic, group, lag),
                group);
    }

    /**
     * Alert when circuit breaker opens.
     */
    public void circuitBreakerOpen(String name) {
        critical("CIRCUIT_BREAKER_OPEN",
                String.format("Circuit breaker '%s' is now OPEN", name),
                name);
    }

    /**
     * Alert when circuit breaker closes.
     */
    public void circuitBreakerClosed(String name) {
        info("CIRCUIT_BREAKER_CLOSED",
                String.format("Circuit breaker '%s' is now CLOSED", name),
                name);
    }

    /**
     * Alert for EOD processing issues.
     */
    public void eodFailed(int accountId, String error) {
        critical("EOD_FAILED",
                String.format("EOD failed for account %d: %s", accountId, error),
                String.valueOf(accountId));
    }

    /**
     * Alert for EOD delay.
     */
    public void eodDelayed(int accountId, long delayMinutes) {
        warning("EOD_DELAYED",
                String.format("EOD for account %d delayed by %d minutes", accountId, delayMinutes),
                String.valueOf(accountId));
    }

    /**
     * Alert for validation issues.
     */
    public void validationFailed(String entityType, String entityId, String details) {
        warning("VALIDATION_FAILED",
                String.format("%s %s validation failed: %s", entityType, entityId, details),
                entityId);
    }

    /**
     * Alert for reconciliation mismatch.
     */
    public void reconciliationMismatch(int accountId, int mismatchCount) {
        warning("RECONCILIATION_MISMATCH",
                String.format("Reconciliation found %d mismatches for account %d", 
                        mismatchCount, accountId),
                String.valueOf(accountId));
    }

    /**
     * Alert for external service unavailability.
     */
    public void serviceUnavailable(String serviceName) {
        critical("SERVICE_UNAVAILABLE",
                String.format("Service '%s' is unavailable", serviceName),
                serviceName);
    }

    /**
     * Alert for FIX connection issues.
     */
    public void fixConnectionLost(String sessionId) {
        critical("FIX_CONNECTION_LOST",
                String.format("FIX session '%s' disconnected", sessionId),
                sessionId);
    }

    /**
     * Alert for FIX connection restored.
     */
    public void fixConnectionRestored(String sessionId) {
        info("FIX_CONNECTION_RESTORED",
                String.format("FIX session '%s' reconnected", sessionId),
                sessionId);
    }

    /**
     * Alert for orphaned orders.
     */
    public void orphanedOrdersDetected(int count) {
        warning("ORPHANED_ORDERS",
                String.format("%d orphaned orders detected", count),
                "ORDERS");
    }

    /**
     * Alert for database connection issues.
     */
    public void databaseConnectionIssue(String details) {
        critical("DATABASE_CONNECTION_ISSUE",
                details,
                "DATABASE");
    }

    /**
     * Alert for memory pressure.
     */
    public void memoryPressure(long usedMb, long maxMb, double percentage) {
        warning("MEMORY_PRESSURE",
                String.format("Memory usage: %dMB / %dMB (%.1f%%)", usedMb, maxMb, percentage),
                "JVM");
    }

    // ════════════════════════════════════════════════════════════════════════
    // THROTTLING
    // ════════════════════════════════════════════════════════════════════════

    private boolean shouldThrottle(String alertType) {
        Long lastTime = lastAlertTime.get(alertType);
        if (lastTime == null) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        return elapsed < ALERT_THROTTLE_SECONDS;
    }

    private void recordAlertTime(String alertType) {
        lastAlertTime.put(alertType, System.currentTimeMillis());
    }

    /**
     * Clear alert throttle state (for testing).
     */
    public void clearThrottleState() {
        lastAlertTime.clear();
    }
}
