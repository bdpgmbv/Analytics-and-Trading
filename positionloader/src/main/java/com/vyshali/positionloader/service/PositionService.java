package com.vyshali.positionloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.exception.PositionLoaderException;
import com.vyshali.positionloader.repository.DataRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Single service for all business logic.
 * Replaces: SnapshotService, MspmService, EodService, EventService, MetricsService, etc.
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

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD PROCESSING (with idempotency)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processEod(Integer accountId) {
        LocalDate today = LocalDate.now();

        // IDEMPOTENCY CHECK: Skip if already completed
        if (repo.isEodCompleted(accountId, today)) {
            log.info("EOD already completed for account {}, skipping", accountId);
            return;
        }

        log.info("Starting EOD for account {}", accountId);

        try {
            repo.recordEodStart(accountId, today);

            // Fetch from MSPM
            Dto.AccountSnapshot snapshot = fetchFromMspm(accountId, today);
            if (snapshot == null) {
                throw new PositionLoaderException("MSPM returned null", accountId, true);
            }

            // Save reference data
            repo.ensureReferenceData(snapshot);

            // Insert positions
            int batchId = repo.getNextBatchId(accountId);
            int count = 0;
            if (!snapshot.isEmpty()) {
                List<Dto.Position> valid = filterValidPositions(snapshot.positions(), accountId);
                count = repo.insertPositions(accountId, valid, "MSPM_EOD", batchId, today);
            }

            // Complete
            repo.recordEodComplete(accountId, today, count);
            repo.markAccountComplete(accountId, snapshot.clientId(), today);

            // Calculate and save hedge valuation (100%)
            java.math.BigDecimal hedgeValue = repo.getHedgeValuation(accountId, today);
            repo.saveHedgeValuation(accountId, today, hedgeValue);
            log.debug("Hedge valuation for account {}: {}", accountId, hedgeValue);

            // Check if all client accounts complete, publish sign-off
            if (repo.isClientComplete(snapshot.clientId(), today)) {
                publishEvent("CLIENT_SIGNOFF", accountId, snapshot.clientId(), repo.countClientAccounts(snapshot.clientId()));
                log.info("Client {} sign-off complete", snapshot.clientId());
            }

            // Publish event
            publishEvent("EOD_COMPLETE", accountId, snapshot.clientId(), count);

            // Metrics
            metrics.counter("posloader.eod.success").increment();

            log.info("EOD complete for account {} ({} positions)", accountId, count);

        } catch (Exception e) {
            repo.recordEodFailed(accountId, today, e.getMessage());
            repo.log("EOD_FAILED", accountId.toString(), "SYSTEM", e.getMessage());
            metrics.counter("posloader.eod.failed").increment();
            log.error("EOD failed for account {}: {}", accountId, e.getMessage());
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void processIntraday(Dto.AccountSnapshot snapshot) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot");
        }

        repo.ensureReferenceData(snapshot);

        if (!snapshot.isEmpty()) {
            int batchId = repo.getNextBatchId(snapshot.accountId());
            repo.insertPositions(snapshot.accountId(), snapshot.positions(), "INTRADAY", batchId, LocalDate.now());
            publishEvent("INTRADAY", snapshot.accountId(), snapshot.clientId(), snapshot.positionCount());
            metrics.counter("posloader.intraday").increment(snapshot.positionCount());
        }

        log.debug("Processed intraday for account {}", snapshot.accountId());
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

        int batchId = repo.getNextBatchId(accountId);
        int count = repo.insertPositions(accountId, positions, "UPLOAD", batchId, LocalDate.now());

        repo.log("UPLOAD", accountId.toString(), "USER", "Uploaded " + count + " positions");
        metrics.counter("posloader.upload").increment(count);

        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MSPM CLIENT (with retry)
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
        metrics.counter("posloader.mspm.failures").increment();
        throw new PositionLoaderException("MSPM unavailable: " + e.getMessage(), accountId, false);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private List<Dto.Position> filterValidPositions(List<Dto.Position> positions, Integer accountId) {
        List<Dto.Position> valid = new ArrayList<>();
        int zeroPriceCount = 0;

        for (Dto.Position pos : positions) {
            if (pos == null) continue;
            if (pos.hasZeroPrice()) {
                zeroPriceCount++;
                log.warn("Zero price: account={}, product={}", accountId, pos.productId());
            }
            valid.add(pos);
        }

        if (zeroPriceCount > 0) {
            metrics.counter("posloader.zero.prices").increment(zeroPriceCount);
        }

        return valid;
    }

    private void publishEvent(String type, Integer accountId, Integer clientId, int count) {
        var event = new Dto.PositionChangeEvent(type, accountId, clientId, count);
        kafka.send(AppConfig.TOPIC_POSITION_CHANGES, accountId.toString(), event);
        log.debug("Published {} event for account {}", type, accountId);
    }
}