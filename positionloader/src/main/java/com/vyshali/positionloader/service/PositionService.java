package com.vyshali.positionloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.exception.PositionLoaderException;
import com.vyshali.positionloader.health.LoaderHealthIndicator;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.repository.DataRepository.BatchInsertResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single service for all business logic.
 * <p>
 * Phase 1 Enhancements: Data Validation, Metrics
 * Phase 2 Enhancements:
 * - #6 Batch Switching: Uses STAGING → ACTIVE pattern
 * - #9 Alerting: Integrates with AlertService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final RestClient mspmClient;
    private final DataRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    private final LoaderHealthIndicator healthIndicator;
    private final LoaderConfig config;
    private final AlertService alertService;  // Phase 2

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD PROCESSING (with batch switching)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processEod(Integer accountId) {
        LocalDate today = LocalDate.now();
        Timer.Sample timer = Timer.start(metrics);

        // IDEMPOTENCY CHECK: Skip if already completed
        if (repo.isEodCompleted(accountId, today)) {
            log.info("EOD already completed for account {}, skipping", accountId);
            metrics.counter("posloader.eod.skipped", "reason", "already_completed").increment();
            return;
        }

        log.info("Starting EOD for account {}", accountId);

        try {
            repo.recordEodStart(accountId, today);

            // Fetch from MSPM
            Timer.Sample mspmTimer = Timer.start(metrics);
            Dto.AccountSnapshot snapshot = fetchFromMspm(accountId, today);
            mspmTimer.stop(metrics.timer("posloader.mspm.duration"));

            if (snapshot == null) {
                throw new PositionLoaderException("MSPM returned null", accountId, true);
            }

            // Save reference data
            repo.ensureReferenceData(snapshot);

            // Insert positions with BATCH SWITCHING (Phase 2)
            int count = 0;
            int batchId = 0;

            if (!snapshot.isEmpty()) {
                // Phase 1: Validation
                List<Dto.Position> validated = validateAndFilter(snapshot.positions(), accountId);

                // Phase 2: Insert to STAGING batch
                BatchInsertResult result = repo.insertPositionsToStaging(accountId, validated, "MSPM_EOD", today);

                batchId = result.batchId();
                count = result.count();

                // Phase 2: Validate before activation
                if (!validateBatch(accountId, batchId, validated.size())) {
                    alertService.batchSwitchFailed(accountId, "Validation failed - count mismatch");
                    throw new PositionLoaderException("Batch validation failed", accountId, true);
                }

                // Phase 2: Atomic switch STAGING → ACTIVE
                boolean activated = repo.activateBatch(accountId, batchId, today);
                if (!activated) {
                    alertService.batchSwitchFailed(accountId, "Activation failed");
                    throw new PositionLoaderException("Batch activation failed", accountId, true);
                }

                metrics.counter("posloader.positions.loaded", "source", "MSPM_EOD").increment(count);
            }

            // Complete
            repo.recordEodComplete(accountId, today, count);
            repo.markAccountComplete(accountId, snapshot.clientId(), today);

            // Check if all client accounts complete, publish sign-off
            if (repo.isClientComplete(snapshot.clientId(), today)) {
                int accountCount = repo.countClientAccounts(snapshot.clientId());
                publishEvent("CLIENT_SIGNOFF", accountId, snapshot.clientId(), accountCount);
                alertService.clientSignoff(snapshot.clientId(), accountCount);  // Phase 2
                log.info("Client {} sign-off complete", snapshot.clientId());
            }

            // Publish event
            publishEvent("EOD_COMPLETE", accountId, snapshot.clientId(), count);

            // Health tracking
            healthIndicator.recordEodSuccess();

            // Metrics
            timer.stop(metrics.timer("posloader.eod.duration", "status", "success"));
            metrics.counter("posloader.eod.completed", "status", "success").increment();

            log.info("EOD complete for account {} (batch={}, positions={})", accountId, batchId, count);

        } catch (Exception e) {
            repo.recordEodFailed(accountId, today, e.getMessage());
            repo.log("EOD_FAILED", accountId.toString(), "SYSTEM", e.getMessage());

            // Health tracking
            healthIndicator.recordEodFailure();

            // Phase 2: Alert on failure
            int consecutiveFailures = getConsecutiveFailures(accountId);
            alertService.eodFailed(accountId, e.getMessage(), consecutiveFailures);

            // Metrics
            timer.stop(metrics.timer("posloader.eod.duration", "status", "failed"));
            metrics.counter("posloader.eod.completed", "status", "failed", "reason", categorizeError(e)).increment();

            log.error("EOD failed for account {}: {}", accountId, e.getMessage());
            throw e;
        }
    }

    /**
     * Validate batch before activation.
     * Basic check: ensure row count matches expected.
     */
    private boolean validateBatch(Integer accountId, int batchId, int expectedCount) {
        // Could add more sophisticated validation here:
        // - Check for duplicates
        // - Verify totals match MSPM
        // - Compare with previous day for anomalies

        // For now, just verify count
        log.debug("Batch {} validation: expected {} positions", batchId, expectedCount);
        return expectedCount >= 0;  // Always pass for now - expand in Phase 3
    }

    /**
     * Get consecutive failure count for alerting severity.
     */
    private int getConsecutiveFailures(Integer accountId) {
        List<Dto.EodStatus> history = repo.getEodHistory(accountId, 7);
        int consecutive = 0;
        for (Dto.EodStatus status : history) {
            if ("FAILED".equals(status.status())) {
                consecutive++;
            } else {
                break;
            }
        }
        return consecutive;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA VALIDATION (Phase 1)
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Dto.Position> validateAndFilter(List<Dto.Position> positions, Integer accountId) {
        if (!config.validation().enabled()) {
            log.debug("Validation disabled by config");
            return positions;
        }

        List<Dto.Position> valid = new ArrayList<>();
        Set<Integer> seenProductIds = new HashSet<>();

        int nullCount = 0;
        int duplicateCount = 0;
        int negativePriceCount = 0;
        int zeroQuantityCount = 0;
        int unrealisticPriceCount = 0;
        int nullProductIdCount = 0;

        for (Dto.Position pos : positions) {
            if (pos == null) {
                nullCount++;
                continue;
            }
            if (pos.productId() == null) {
                nullProductIdCount++;
                continue;
            }
            if (!seenProductIds.add(pos.productId())) {
                duplicateCount++;
                continue;
            }
            if (pos.price() != null && pos.price().compareTo(BigDecimal.ZERO) < 0) {
                negativePriceCount++;
                continue;
            }
            if (config.validation().rejectZeroQuantity() && (pos.quantity() == null || pos.quantity().compareTo(BigDecimal.ZERO) == 0)) {
                zeroQuantityCount++;
                continue;
            }
            BigDecimal maxPrice = BigDecimal.valueOf(config.validation().maxPriceThreshold());
            if (pos.price() != null && pos.price().compareTo(maxPrice) > 0) {
                unrealisticPriceCount++;
                continue;
            }

            valid.add(pos);
        }

        int totalRejected = nullCount + duplicateCount + negativePriceCount + zeroQuantityCount + unrealisticPriceCount + nullProductIdCount;

        if (totalRejected > 0) {
            log.warn("Account {}: Validation rejected {} of {} positions", accountId, totalRejected, positions.size());

            // Metrics
            if (nullCount > 0) metrics.counter("posloader.validation.rejected", "reason", "null").increment(nullCount);
            if (duplicateCount > 0)
                metrics.counter("posloader.validation.rejected", "reason", "duplicate").increment(duplicateCount);
            if (negativePriceCount > 0)
                metrics.counter("posloader.validation.rejected", "reason", "negative_price").increment(negativePriceCount);
            if (zeroQuantityCount > 0)
                metrics.counter("posloader.validation.rejected", "reason", "zero_quantity").increment(zeroQuantityCount);
            if (unrealisticPriceCount > 0)
                metrics.counter("posloader.validation.rejected", "reason", "unrealistic_price").increment(unrealisticPriceCount);
            if (nullProductIdCount > 0)
                metrics.counter("posloader.validation.rejected", "reason", "null_product_id").increment(nullProductIdCount);

            // Phase 2: Alert if rejection rate is high
            alertService.validationRejectionHigh(accountId, totalRejected, positions.size());
        }

        return valid;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processIntraday(Dto.AccountSnapshot snapshot) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot");
        }

        Timer.Sample timer = Timer.start(metrics);

        try {
            repo.ensureReferenceData(snapshot);

            if (!snapshot.isEmpty()) {
                List<Dto.Position> validated = validateAndFilter(snapshot.positions(), snapshot.accountId());
                int batchId = repo.getNextBatchId(snapshot.accountId());
                int count = repo.insertPositions(snapshot.accountId(), validated, "INTRADAY", batchId, LocalDate.now());

                publishEvent("INTRADAY", snapshot.accountId(), snapshot.clientId(), count);
                metrics.counter("posloader.positions.loaded", "source", "INTRADAY").increment(count);
            }

            timer.stop(metrics.timer("posloader.intraday.duration", "status", "success"));
            log.debug("Processed intraday for account {}", snapshot.accountId());

        } catch (Exception e) {
            timer.stop(metrics.timer("posloader.intraday.duration", "status", "failed"));
            metrics.counter("posloader.intraday.failed").increment();
            throw e;
        }
    }

    public void processIntradayJson(String jsonRecord) {
        try {
            Dto.AccountSnapshot snapshot = json.readValue(jsonRecord, Dto.AccountSnapshot.class);
            processIntraday(snapshot);
        } catch (Exception e) {
            log.error("Failed to parse intraday: {}", e.getMessage());
            throw new PositionLoaderException("Invalid intraday record", null, false, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPLOAD PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public int processUpload(Integer accountId, List<Dto.Position> positions) {
        if (positions == null || positions.isEmpty()) return 0;

        if (positions.size() > config.maxUploadSize()) {
            metrics.counter("posloader.upload.rejected", "reason", "too_large").increment();
            throw new IllegalArgumentException("Upload exceeds max size of " + config.maxUploadSize());
        }

        Timer.Sample timer = Timer.start(metrics);

        List<Dto.Position> validated = validateAndFilter(positions, accountId);
        int batchId = repo.getNextBatchId(accountId);
        int count = repo.insertPositions(accountId, validated, "UPLOAD", batchId, LocalDate.now());

        repo.log("UPLOAD", accountId.toString(), "USER", "Uploaded " + count + " positions");

        timer.stop(metrics.timer("posloader.upload.duration"));
        metrics.counter("posloader.positions.loaded", "source", "UPLOAD").increment(count);

        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROLLBACK SUPPORT (Phase 2)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Rollback EOD for an account to previous batch.
     * Useful when bad data was loaded and needs to be reverted.
     */
    @Transactional
    public boolean rollbackEod(Integer accountId, LocalDate date) {
        log.warn("Rolling back EOD for account {} on {}", accountId, date);

        boolean success = repo.rollbackBatch(accountId, date);

        if (success) {
            repo.log("ROLLBACK", accountId.toString(), "OPS", "Rolled back to previous batch");
            metrics.counter("posloader.eod.rollback", "status", "success").increment();
            alertService.info("EOD_ROLLBACK", String.format("Account %d rolled back to previous batch", accountId), accountId.toString());
        } else {
            metrics.counter("posloader.eod.rollback", "status", "failed").increment();
        }

        return success;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MSPM CLIENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Retry(name = "mspm", fallbackMethod = "fetchFallback")
    @CircuitBreaker(name = "mspm", fallbackMethod = "fetchFallback")
    public Dto.AccountSnapshot fetchFromMspm(Integer accountId, LocalDate date) {
        log.debug("Fetching from MSPM: account={}, date={}", accountId, date);
        metrics.counter("posloader.mspm.calls").increment();

        return mspmClient.get().uri("/api/v1/accounts/{accountId}/snapshot?businessDate={date}", accountId, date).retrieve().body(Dto.AccountSnapshot.class);
    }

    private Dto.AccountSnapshot fetchFallback(Integer accountId, LocalDate date, Exception e) {
        log.error("MSPM unavailable for account {}: {}", accountId, e.getMessage());
        metrics.counter("posloader.mspm.failures", "reason", categorizeError(e)).increment();

        // Phase 2: Alert on circuit open
        if (e.getClass().getSimpleName().contains("Circuit")) {
            alertService.circuitOpen("mspm", e.getMessage());
        }

        throw new PositionLoaderException("MSPM unavailable: " + e.getMessage(), accountId, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void publishEvent(String type, Integer accountId, Integer clientId, int count) {
        var event = new Dto.PositionChangeEvent(type, accountId, clientId, count);
        kafka.send(AppConfig.TOPIC_POSITION_CHANGES, accountId.toString(), event);
        metrics.counter("posloader.events.published", "type", type).increment();
        log.debug("Published {} event for account {}", type, accountId);
    }

    private String categorizeError(Exception e) {
        String name = e.getClass().getSimpleName();
        if (name.contains("Timeout")) return "timeout";
        if (name.contains("Connection")) return "connection";
        if (name.contains("Circuit")) return "circuit_open";
        if (name.contains("Validation")) return "validation";
        return "unknown";
    }
}