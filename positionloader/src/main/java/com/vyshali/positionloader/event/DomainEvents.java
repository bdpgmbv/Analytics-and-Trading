package com.vyshali.positionloader.event;

/*
 * 12/10/2025 - NEW: Domain events for internal decoupling
 *
 * PURPOSE:
 * Use Spring Application Events for communication within Position Loader.
 * This provides:
 * - Zero latency (same JVM)
 * - Clean separation of concerns
 * - Easy testing (mock event listeners)
 * - Transaction-aware publishing
 *
 * PATTERN:
 * Service publishes event → Spring routes to listeners → Listeners handle async
 *
 * @author Vyshali Prabananth Lal
 */

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Domain events for Position Loader.
 * All events are immutable records.
 */
public final class DomainEvents {

    private DomainEvents() {
    } // Prevent instantiation

    // ==================== EOD EVENTS ====================

    /**
     * Published when EOD processing starts for an account.
     */
    public record EodStarted(Integer accountId, Integer clientId, LocalDate businessDate, Instant timestamp) {
        public EodStarted(Integer accountId, Integer clientId, LocalDate businessDate) {
            this(accountId, clientId, businessDate, Instant.now());
        }
    }

    /**
     * Published when EOD processing completes successfully for an account.
     */
    public record EodCompleted(Integer accountId, Integer clientId, LocalDate businessDate, int positionCount,
                               long durationMs, Instant timestamp) {
        public EodCompleted(Integer accountId, Integer clientId, LocalDate businessDate, int positionCount, long durationMs) {
            this(accountId, clientId, businessDate, positionCount, durationMs, Instant.now());
        }
    }

    /**
     * Published when EOD processing fails for an account.
     */
    public record EodFailed(Integer accountId, Integer clientId, LocalDate businessDate, String errorMessage,
                            String errorType, Instant timestamp) {
        public EodFailed(Integer accountId, Integer clientId, LocalDate businessDate, String errorMessage, String errorType) {
            this(accountId, clientId, businessDate, errorMessage, errorType, Instant.now());
        }
    }

    /**
     * Published when all accounts for a client complete EOD.
     */
    public record ClientSignOffCompleted(Integer clientId, LocalDate businessDate, int totalAccounts,
                                         long totalDurationMs, Instant timestamp) {
        public ClientSignOffCompleted(Integer clientId, LocalDate businessDate, int totalAccounts, long totalDurationMs) {
            this(clientId, businessDate, totalAccounts, totalDurationMs, Instant.now());
        }
    }

    // ==================== POSITION EVENTS ====================

    /**
     * Published when positions are updated (EOD or Intraday).
     */
    public record PositionsUpdated(Integer accountId, Integer clientId, String updateType,
                                   // "EOD", "INTRADAY", "MANUAL_UPLOAD"
                                   int positionCount, int batchId, Instant timestamp) {
        public PositionsUpdated(Integer accountId, Integer clientId, String updateType, int positionCount, int batchId) {
            this(accountId, clientId, updateType, positionCount, batchId, Instant.now());
        }
    }

    /**
     * Published when a batch swap occurs.
     */
    public record BatchSwapped(Integer accountId, int oldBatchId, int newBatchId, Instant timestamp) {
        public BatchSwapped(Integer accountId, int oldBatchId, int newBatchId) {
            this(accountId, oldBatchId, newBatchId, Instant.now());
        }
    }

    // ==================== VALIDATION EVENTS ====================

    /**
     * Published when validation issues are detected.
     */
    public record ValidationIssueDetected(Integer accountId, String issueType,
                                          // "ZERO_PRICE", "MISSING_PRODUCT", "DUPLICATE"
                                          int count, List<String> details, Instant timestamp) {
        public ValidationIssueDetected(Integer accountId, String issueType, int count, List<String> details) {
            this(accountId, issueType, count, details, Instant.now());
        }
    }

    // ==================== CACHE EVENTS ====================

    /**
     * Published when cache should be invalidated.
     * Listeners can notify downstream services.
     */
    public record CacheInvalidationRequested(Integer accountId, Integer clientId, String reason, Instant timestamp) {
        public CacheInvalidationRequested(Integer accountId, Integer clientId, String reason) {
            this(accountId, clientId, reason, Instant.now());
        }
    }

    // ==================== AUDIT EVENTS ====================

    /**
     * Published for audit logging.
     */
    public record AuditEvent(String eventType, String entityType, String entityId, String actor, String details,
                             Instant timestamp) {
        public AuditEvent(String eventType, String entityType, String entityId, String actor, String details) {
            this(eventType, entityType, entityId, actor, details, Instant.now());
        }
    }
}