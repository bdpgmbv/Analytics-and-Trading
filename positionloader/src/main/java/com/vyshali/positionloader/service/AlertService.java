package com.vyshali.positionloader.service;

/*
 * 12/11/2025 - 2:29 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Phase 2 Enhancement #9: Alerting Integration
 * <p>
 * Publishes alerts to SYSTEM_ALERTS Kafka topic.
 * Downstream: PagerDuty, Slack, or other alerting systems consume this topic.
 * <p>
 * Alert Levels:
 * - INFO: Normal operations, for dashboards
 * - WARNING: Attention needed, no immediate action
 * - CRITICAL: Immediate attention required
 * - PAGE: Wake someone up (5+ consecutive failures, data loss risk)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final KafkaTemplate<String, Object> kafka;

    public static final String TOPIC_ALERTS = "SYSTEM_ALERTS";

    // ═══════════════════════════════════════════════════════════════════════════
    // ALERT RECORD
    // ═══════════════════════════════════════════════════════════════════════════

    public record Alert(String level,           // INFO, WARNING, CRITICAL, PAGE
                        String source,          // positionloader
                        String type,            // EOD_FAILED, VALIDATION_HIGH, CIRCUIT_OPEN, etc.
                        String message, String entityId,        // accountId, clientId, etc.
                        Instant timestamp) {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    public void info(String type, String message, String entityId) {
        publish("INFO", type, message, entityId);
    }

    public void warning(String type, String message, String entityId) {
        publish("WARNING", type, message, entityId);
    }

    public void critical(String type, String message, String entityId) {
        publish("CRITICAL", type, message, entityId);
    }

    public void page(String type, String message, String entityId) {
        publish("PAGE", type, message, entityId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPECIFIC ALERT TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    public void eodFailed(Integer accountId, String error, int consecutiveFailures) {
        String level = consecutiveFailures >= 5 ? "PAGE" : consecutiveFailures >= 3 ? "CRITICAL" : "WARNING";

        String message = String.format("EOD failed for account %d: %s (consecutive failures: %d)", accountId, error, consecutiveFailures);

        publish(level, "EOD_FAILED", message, accountId.toString());
    }

    public void validationRejectionHigh(Integer accountId, int rejected, int total) {
        double percent = total > 0 ? (rejected * 100.0 / total) : 0;
        if (percent > 20) {
            String message = String.format("High validation rejection rate for account %d: %d/%d (%.1f%%)", accountId, rejected, total, percent);
            warning("VALIDATION_HIGH", message, accountId.toString());
        }
    }

    public void circuitOpen(String circuitName, String reason) {
        String message = String.format("Circuit breaker '%s' opened: %s", circuitName, reason);
        critical("CIRCUIT_OPEN", message, circuitName);
    }

    public void circuitClosed(String circuitName) {
        String message = String.format("Circuit breaker '%s' closed - service recovered", circuitName);
        info("CIRCUIT_CLOSED", message, circuitName);
    }

    public void dlqThresholdExceeded(int depth, int threshold) {
        String message = String.format("DLQ depth (%d) exceeded threshold (%d)", depth, threshold);
        critical("DLQ_THRESHOLD", message, "dlq");
    }

    public void rateLimitHit(String source, int rejected) {
        String message = String.format("Rate limit hit from %s, rejected %d requests", source, rejected);
        warning("RATE_LIMIT_HIT", message, source);
    }

    public void batchSwitchFailed(Integer accountId, String reason) {
        String message = String.format("Batch switch failed for account %d: %s", accountId, reason);
        critical("BATCH_SWITCH_FAILED", message, accountId.toString());
    }

    public void clientSignoff(Integer clientId, int accountCount) {
        String message = String.format("Client %d sign-off complete (%d accounts)", clientId, accountCount);
        info("CLIENT_SIGNOFF", message, clientId.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CORE PUBLISH
    // ═══════════════════════════════════════════════════════════════════════════

    private void publish(String level, String type, String message, String entityId) {
        Alert alert = new Alert(level, "positionloader", type, message, entityId, Instant.now());

        try {
            kafka.send(TOPIC_ALERTS, entityId, alert);
            log.info("[ALERT] {} - {}: {}", level, type, message);
        } catch (Exception e) {
            // Don't let alerting failures break the main flow
            log.error("Failed to publish alert: {} - {}", type, e.getMessage());
        }
    }
}
