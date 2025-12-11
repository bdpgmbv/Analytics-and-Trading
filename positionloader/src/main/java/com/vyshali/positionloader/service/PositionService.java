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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Position processing service with all Phase 1-4 enhancements.
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
    private final AlertService alertService;
    private final BusinessDayService businessDayService;  // Phase 4

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processEod(Integer accountId) {
        processEod(accountId, LocalDate.now());
    }

    @Transactional
    public void processEod(Integer accountId, LocalDate businessDate) {
        // Phase 4 #18: Feature flag check
        if (!config.features().eodProcessingEnabled()) {
            log.warn("EOD processing disabled by feature flag");
            metrics.counter("posloader.eod.skipped", "reason", "feature_disabled").increment();
            return;
        }

        if (!config.features().isAccountEnabled(accountId)) {
            log.info("Account {} disabled by feature flag", accountId);
            metrics.counter("posloader.eod.skipped", "reason", "account_disabled").increment();
            return;
        }

        // Phase 4 #17: Holiday check
        if (!businessDayService.isBusinessDay(businessDate)) {
            log.info("Skipping EOD for {} - {} is not a business day", accountId, businessDate);
            metrics.counter("posloader.eod.skipped", "reason", "holiday").increment();
            return;
        }

        Timer.Sample timer = Timer.start(metrics);

        // Idempotency check
        if (repo.isEodCompleted(accountId, businessDate)) {
            log.info("EOD already completed for account {} on {}", accountId, businessDate);
            metrics.counter("posloader.eod.skipped", "reason", "already_completed").increment();
            return;
        }

        log.info("Starting EOD for account {} on {}", accountId, businessDate);

        try {
            repo.recordEodStart(accountId, businessDate);

            // Fetch from MSPM
            Dto.AccountSnapshot snapshot = fetchFromMspm(accountId, businessDate);
            if (snapshot == null) {
                throw new PositionLoaderException("MSPM returned null", accountId, true);
            }

            // Phase 4 #16: Duplicate detection
            if (config.features().duplicateDetectionEnabled() && !snapshot.isEmpty()) {
                String contentHash = computeContentHash(snapshot.positions());
                if (repo.isDuplicateSnapshot(accountId, businessDate, contentHash)) {
                    log.info("Duplicate snapshot detected for account {} - same content as previous", accountId);
                    metrics.counter("posloader.eod.skipped", "reason", "duplicate_content").increment();
                    repo.recordEodComplete(accountId, businessDate, 0);
                    return;
                }
                repo.saveSnapshotHash(accountId, businessDate, contentHash);
            }

            repo.ensureReferenceData(snapshot);

            int count = 0;
            int batchId = 0;

            if (!snapshot.isEmpty()) {
                List<Dto.Position> validated = validateAndFilter(snapshot.positions(), accountId);
                BatchInsertResult result = repo.insertPositionsToStaging(accountId, validated, "MSPM_EOD", businessDate);
                batchId = result.batchId();
                count = result.count();

                boolean activated = repo.activateBatch(accountId, batchId, businessDate);
                if (!activated) {
                    alertService.batchSwitchFailed(accountId, "Activation failed");
                    throw new PositionLoaderException("Batch activation failed", accountId, true);
                }

                metrics.counter("posloader.positions.loaded", "source", "MSPM_EOD").increment(count);
            }

            repo.recordEodComplete(accountId, businessDate, count);
            repo.markAccountComplete(accountId, snapshot.clientId(), businessDate);

            if (repo.isClientComplete(snapshot.clientId(), businessDate)) {
                int accountCount = repo.countClientAccounts(snapshot.clientId());
                publishEvent("CLIENT_SIGNOFF", accountId, snapshot.clientId(), accountCount);
                alertService.clientSignoff(snapshot.clientId(), accountCount);
            }

            publishEvent("EOD_COMPLETE", accountId, snapshot.clientId(), count);
            healthIndicator.recordEodSuccess();
            timer.stop(metrics.timer("posloader.eod.duration", "status", "success"));
            metrics.counter("posloader.eod.completed", "status", "success").increment();

            log.info("EOD complete for account {} on {} (batch={}, positions={})", accountId, businessDate, batchId, count);

        } catch (Exception e) {
            repo.recordEodFailed(accountId, businessDate, e.getMessage());
            repo.log("EOD_FAILED", accountId.toString(), "SYSTEM", e.getMessage());
            healthIndicator.recordEodFailure();
            alertService.eodFailed(accountId, e.getMessage(), getConsecutiveFailures(accountId));
            timer.stop(metrics.timer("posloader.eod.duration", "status", "failed"));
            metrics.counter("posloader.eod.completed", "status", "failed").increment();
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #21: LATE ARRIVAL HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process late EOD data (yesterday's data arriving today).
     * Validates that the date is not in the future and not too old.
     */
    @Transactional
    public void processLateEod(Integer accountId, LocalDate businessDate) {
        LocalDate today = LocalDate.now();

        // Validation
        if (businessDate.isAfter(today)) {
            throw new IllegalArgumentException("Cannot process future date: " + businessDate);
        }

        if (businessDate.isBefore(today.minusDays(5))) {
            throw new IllegalArgumentException("Date too old (>5 days): " + businessDate);
        }

        log.warn("Processing LATE EOD for account {} on {} (today is {})", accountId, businessDate, today);
        metrics.counter("posloader.eod.late_arrival").increment();
        repo.log("LATE_EOD", accountId.toString(), "SYSTEM", "Late arrival for " + businessDate);

        // Process normally but with the specified date
        processEod(accountId, businessDate);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #16: DUPLICATE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compute MD5 hash of sorted positions for duplicate detection.
     */
    private String computeContentHash(List<Dto.Position> positions) {
        try {
            // Sort positions by productId for consistent hash
            List<Dto.Position> sorted = positions.stream().sorted(Comparator.comparingInt(Dto.Position::productId)).toList();

            // Build string representation
            StringBuilder sb = new StringBuilder();
            for (Dto.Position p : sorted) {
                sb.append(p.productId()).append(":").append(p.quantity()).append(":").append(p.price()).append(";");
            }

            // Compute MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Failed to compute content hash: {}", e.getMessage());
            return null;  // Skip duplicate check
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #20: MANUAL OVERRIDE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adjust a position manually (ops override).
     */
    @Transactional
    public void adjustPosition(Integer accountId, Integer productId, BigDecimal newQuantity, BigDecimal newPrice, String reason, String actor) {
        log.warn("OPS: Manual position adjustment for account {} product {} by {}", accountId, productId, actor);

        // Get current position
        BigDecimal oldQuantity = repo.getQuantityAsOf(accountId, productId, LocalDate.now());

        // Update
        repo.updatePosition(accountId, productId, newQuantity, newPrice, LocalDate.now());

        // Audit trail
        String auditPayload = String.format("qty: %s -> %s, price: %s, reason: %s", oldQuantity, newQuantity, newPrice, reason);
        repo.log("POSITION_ADJUSTMENT", accountId + ":" + productId, actor, auditPayload);

        metrics.counter("posloader.ops.position_adjusted").increment();
        alertService.info("POSITION_ADJUSTED", String.format("Account %d product %d adjusted by %s", accountId, productId, actor), accountId.toString());
    }

    /**
     * Reset EOD status to allow reprocessing.
     */
    @Transactional
    public void resetEodStatus(Integer accountId, LocalDate date, String actor) {
        log.warn("OPS: Resetting EOD status for account {} on {} by {}", accountId, date, actor);

        repo.resetEodStatus(accountId, date);
        repo.log("EOD_RESET", accountId.toString(), actor, "Reset for reprocessing");

        metrics.counter("posloader.ops.eod_reset").increment();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Dto.Position> validateAndFilter(List<Dto.Position> positions, Integer accountId) {
        if (!config.validation().enabled() && !config.features().validationEnabled()) {
            return positions;
        }

        List<Dto.Position> valid = new ArrayList<>();
        Set<Integer> seenProductIds = new HashSet<>();
        int rejected = 0;

        for (Dto.Position pos : positions) {
            if (pos == null) {
                rejected++;
                continue;
            }
            if (pos.productId() == null) {
                rejected++;
                continue;
            }
            if (!seenProductIds.add(pos.productId())) {
                rejected++;
                continue;
            }
            if (pos.price() != null && pos.price().compareTo(BigDecimal.ZERO) < 0) {
                rejected++;
                continue;
            }
            if (config.validation().rejectZeroQuantity() && (pos.quantity() == null || pos.quantity().compareTo(BigDecimal.ZERO) == 0)) {
                rejected++;
                continue;
            }
            if (pos.price() != null && pos.price().compareTo(BigDecimal.valueOf(config.validation().maxPriceThreshold())) > 0) {
                rejected++;
                continue;
            }

            valid.add(pos);
        }

        if (rejected > 0) {
            log.warn("Account {}: Validation rejected {} of {} positions", accountId, rejected, positions.size());
            metrics.counter("posloader.validation.rejected").increment(rejected);
            alertService.validationRejectionHigh(accountId, rejected, positions.size());
        }

        return valid;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processIntraday(Dto.AccountSnapshot snapshot) {
        if (!config.features().intradayProcessingEnabled()) {
            metrics.counter("posloader.intraday.skipped", "reason", "feature_disabled").increment();
            return;
        }

        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot");
        }

        repo.ensureReferenceData(snapshot);
        if (!snapshot.isEmpty()) {
            List<Dto.Position> validated = validateAndFilter(snapshot.positions(), snapshot.accountId());
            int batchId = repo.getNextBatchId(snapshot.accountId());
            int count = repo.insertPositions(snapshot.accountId(), validated, "INTRADAY", batchId, LocalDate.now());
            publishEvent("INTRADAY", snapshot.accountId(), snapshot.clientId(), count);
            metrics.counter("posloader.positions.loaded", "source", "INTRADAY").increment(count);
        }
    }

    public void processIntradayJson(String jsonRecord) {
        try {
            Dto.AccountSnapshot snapshot = json.readValue(jsonRecord, Dto.AccountSnapshot.class);
            processIntraday(snapshot);
        } catch (Exception e) {
            throw new PositionLoaderException("Invalid intraday record", null, false, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPLOAD & ROLLBACK
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public int processUpload(Integer accountId, List<Dto.Position> positions) {
        if (positions == null || positions.isEmpty()) return 0;
        if (positions.size() > config.maxUploadSize()) {
            throw new IllegalArgumentException("Upload exceeds max size of " + config.maxUploadSize());
        }

        List<Dto.Position> validated = validateAndFilter(positions, accountId);
        int batchId = repo.getNextBatchId(accountId);
        int count = repo.insertPositions(accountId, validated, "UPLOAD", batchId, LocalDate.now());
        repo.log("UPLOAD", accountId.toString(), "USER", "Uploaded " + count + " positions");
        metrics.counter("posloader.positions.loaded", "source", "UPLOAD").increment(count);
        return count;
    }

    @Transactional
    public boolean rollbackEod(Integer accountId, LocalDate date) {
        log.warn("Rolling back EOD for account {} on {}", accountId, date);
        boolean success = repo.rollbackBatch(accountId, date);
        if (success) {
            repo.log("ROLLBACK", accountId.toString(), "OPS", "Rolled back to previous batch");
            metrics.counter("posloader.eod.rollback", "status", "success").increment();
            alertService.info("EOD_ROLLBACK", "Account " + accountId + " rolled back", accountId.toString());
        }
        return success;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MSPM CLIENT
    // ═══════════════════════════════════════════════════════════════════════════

    @Retry(name = "mspm", fallbackMethod = "fetchFallback")
    @CircuitBreaker(name = "mspm", fallbackMethod = "fetchFallback")
    public Dto.AccountSnapshot fetchFromMspm(Integer accountId, LocalDate date) {
        metrics.counter("posloader.mspm.calls").increment();
        return mspmClient.get().uri("/api/v1/accounts/{accountId}/snapshot?businessDate={date}", accountId, date).retrieve().body(Dto.AccountSnapshot.class);
    }

    private Dto.AccountSnapshot fetchFallback(Integer accountId, LocalDate date, Exception e) {
        metrics.counter("posloader.mspm.failures").increment();
        throw new PositionLoaderException("MSPM unavailable: " + e.getMessage(), accountId, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void publishEvent(String type, Integer accountId, Integer clientId, int count) {
        kafka.send(AppConfig.TOPIC_POSITION_CHANGES, accountId.toString(), new Dto.PositionChangeEvent(type, accountId, clientId, count));
        metrics.counter("posloader.events.published", "type", type).increment();
    }

    private int getConsecutiveFailures(Integer accountId) {
        return (int) repo.getEodHistory(accountId, 7).stream().takeWhile(s -> "FAILED".equals(s.status())).count();
    }
}