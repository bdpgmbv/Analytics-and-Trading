package com.vyshali.positionloader.event;

/*
 * 12/10/2025 - NEW: Domain event listeners
 *
 * PURPOSE:
 * Handle internal domain events for:
 * - Metrics recording
 * - Audit logging
 * - Downstream notifications
 * - Cache invalidation
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.service.MetricsService;
import com.vyshali.positionloader.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListeners {

    private final MetricsService metrics;
    private final AuditRepository audit;
    private final NotificationService notifications;

    // ==================== EOD EVENT HANDLERS ====================

    /**
     * Handle EOD started - record metrics.
     */
    @EventListener
    public void onEodStarted(DomainEvents.EodStarted event) {
        log.info("EOD started: account={}, date={}", event.accountId(), event.businessDate());
        // Metrics are handled in SnapshotService, but we could add more here
    }

    /**
     * Handle EOD completed - record metrics, notify downstream.
     * Runs AFTER transaction commits to ensure data is persisted.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEodCompleted(DomainEvents.EodCompleted event) {
        log.info("EOD completed: account={}, positions={}, duration={}ms", event.accountId(), event.positionCount(), event.durationMs());

        // Record success metric
        metrics.recordAccountSuccess();

        // Notify downstream services (async REST)
        notifications.notifyPositionChange(event.accountId(), event.clientId(), "EOD_COMPLETE");
    }

    /**
     * Handle EOD failed - record metrics, log for alerting.
     */
    @EventListener
    public void onEodFailed(DomainEvents.EodFailed event) {
        log.error("EOD failed: account={}, error={}, type={}", event.accountId(), event.errorMessage(), event.errorType());

        metrics.recordAccountFailure();

        // Audit log the failure
        audit.log("EOD_FAILED", event.accountId().toString(), "SYSTEM", "Error: " + event.errorMessage());
    }

    /**
     * Handle client sign-off - notify reporting systems.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClientSignOff(DomainEvents.ClientSignOffCompleted event) {
        log.info("Client sign-off: client={}, accounts={}, date={}", event.clientId(), event.totalAccounts(), event.businessDate());

        // This would typically trigger downstream reporting
        audit.log("CLIENT_SIGNOFF", event.clientId().toString(), "SYSTEM", String.format("Completed %d accounts for %s", event.totalAccounts(), event.businessDate()));
    }

    // ==================== POSITION EVENT HANDLERS ====================

    /**
     * Handle positions updated - notify downstream for cache invalidation.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPositionsUpdated(DomainEvents.PositionsUpdated event) {
        log.debug("Positions updated: account={}, type={}, count={}", event.accountId(), event.updateType(), event.positionCount());

        // Notify Price and Hedge services
        notifications.notifyPositionChange(event.accountId(), event.clientId(), event.updateType());
    }

    /**
     * Handle batch swap - could trigger reconciliation.
     */
    @EventListener
    public void onBatchSwapped(DomainEvents.BatchSwapped event) {
        log.info("Batch swapped: account={}, {} → {}", event.accountId(), event.oldBatchId(), event.newBatchId());
    }

    // ==================== VALIDATION EVENT HANDLERS ====================

    /**
     * Handle validation issues - record metrics, possibly alert.
     */
    @EventListener
    public void onValidationIssue(DomainEvents.ValidationIssueDetected event) {
        log.warn("Validation issue: account={}, type={}, count={}", event.accountId(), event.issueType(), event.count());

        switch (event.issueType()) {
            case "ZERO_PRICE" -> metrics.recordZeroPriceDetected(event.count());
            case "DUPLICATE" -> log.debug("Duplicates filtered: {}", event.count());
            default -> metrics.recordValidationError();
        }
    }

    // ==================== CACHE EVENT HANDLERS ====================

    /**
     * Handle cache invalidation requests - forward to notification service.
     */
    @Async
    @EventListener
    public void onCacheInvalidation(DomainEvents.CacheInvalidationRequested event) {
        log.debug("Cache invalidation: account={}, reason={}", event.accountId(), event.reason());

        notifications.notifyPositionChange(event.accountId(), event.clientId(), "CACHE_INVALIDATE");
    }

    // ==================== AUDIT EVENT HANDLERS ====================

    /**
     * Handle audit events - persist to audit log.
     */
    @Async
    @EventListener
    public void onAuditEvent(DomainEvents.AuditEvent event) {
        audit.log(event.eventType(), event.entityId(), event.actor(), event.details());
    }
}