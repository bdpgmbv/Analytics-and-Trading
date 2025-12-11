package com.vyshali.positionloader.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing alerts and notifications.
 */
@Service
public class AlertService {
    
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    
    private final MeterRegistry meterRegistry;
    private final Counter alertsRaised;
    
    public AlertService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.alertsRaised = Counter.builder("alerts.raised")
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
        log.warn("ALERT [{}] {}: {}", alertType, context, message);
        meterRegistry.counter("alerts.warning", "type", alertType).increment();
        alertsRaised.increment();
    }
    
    /**
     * Raise a critical-level alert.
     */
    public void critical(String alertType, String message, String context) {
        log.error("CRITICAL ALERT [{}] {}: {}", alertType, context, message);
        meterRegistry.counter("alerts.critical", "type", alertType).increment();
        alertsRaised.increment();
    }
    
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
     * Alert when EOD is delayed.
     */
    public void eodDelayed(int accountId, long delayMinutes) {
        warning("EOD_DELAYED",
            String.format("EOD for account %d delayed by %d minutes", accountId, delayMinutes),
            String.valueOf(accountId));
    }
    
    /**
     * Alert when EOD fails.
     */
    public void eodFailed(int accountId, String error) {
        critical("EOD_FAILED",
            String.format("EOD failed for account %d: %s", accountId, error),
            String.valueOf(accountId));
    }
    
    /**
     * Alert for high consumer lag.
     */
    public void highConsumerLag(String topic, String group, long lag) {
        warning("HIGH_CONSUMER_LAG",
            String.format("Consumer lag for %s/%s: %d messages", topic, group, lag),
            group);
    }
    
    /**
     * Alert for validation issues.
     */
    public void validationIssue(int accountId, String issue) {
        warning("VALIDATION_ISSUE",
            String.format("Validation issue for account %d: %s", accountId, issue),
            String.valueOf(accountId));
    }
    
    /**
     * Alert for reconciliation mismatch.
     */
    public void reconciliationMismatch(int accountId, int mismatchCount) {
        warning("RECONCILIATION_MISMATCH",
            String.format("Reconciliation found %d mismatches for account %d", mismatchCount, accountId),
            String.valueOf(accountId));
    }
}
