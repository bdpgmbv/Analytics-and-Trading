package com.vyshali.positionloader.metrics;

/*
 * 12/09/2025 - 3:49 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #7: Business KPI Metrics
 *
 * Issue #11: "Time for each request – it was all over the code"
 *
 * Problem: No business metrics, can't measure SLAs, no alerting
 *
 * @author Vyshali Prabananth Lal
 */

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoaderMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter positionsLoadedCounter;
    private final Counter accountsProcessedCounter;
    private final Counter accountsFailedCounter;
    private final Counter validationErrorsCounter;
    private final Counter validationWarningsCounter;
    private final Counter zeroPriceDetectedCounter;
    private final Counter mspmCallsCounter;
    private final Counter mspmFailuresCounter;
    private final Counter cacheHitsCounter;
    private final Counter cacheMissesCounter;

    // Gauges
    private final AtomicInteger pendingAccountsGauge;
    private final AtomicInteger activeConnectionsGauge;
    private final AtomicLong lastEodDurationGauge;
    private final AtomicInteger lastEodSuccessGauge;
    private final AtomicInteger lastEodFailedGauge;

    // Timers
    private final Timer accountProcessingTimer;
    private final Timer mspmFetchTimer;
    private final Timer dbSaveTimer;
    private final Timer validationTimer;
    private final Timer bulkInsertTimer;

    // Distribution Summaries
    private final DistributionSummary positionsPerAccountSummary;
    private final DistributionSummary snapshotSizeSummary;

    public LoaderMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Counters
        this.positionsLoadedCounter = Counter.builder("posloader.positions.loaded.total").description("Total positions loaded").register(registry);

        this.accountsProcessedCounter = Counter.builder("posloader.accounts.processed.total").description("Total accounts processed").tag("status", "success").register(registry);

        this.accountsFailedCounter = Counter.builder("posloader.accounts.processed.total").description("Total accounts failed").tag("status", "failed").register(registry);

        this.validationErrorsCounter = Counter.builder("posloader.validation.issues").description("Validation errors").tag("severity", "error").register(registry);

        this.validationWarningsCounter = Counter.builder("posloader.validation.issues").description("Validation warnings").tag("severity", "warning").register(registry);

        this.zeroPriceDetectedCounter = Counter.builder("posloader.zero_price.detected").description("Zero-price positions detected").register(registry);

        this.mspmCallsCounter = Counter.builder("posloader.mspm.calls.total").description("Total MSPM API calls").register(registry);

        this.mspmFailuresCounter = Counter.builder("posloader.mspm.failures.total").description("MSPM API call failures").register(registry);

        this.cacheHitsCounter = Counter.builder("posloader.cache.hits").description("Cache hits").register(registry);

        this.cacheMissesCounter = Counter.builder("posloader.cache.misses").description("Cache misses").register(registry);

        // Gauges
        this.pendingAccountsGauge = new AtomicInteger(0);
        Gauge.builder("posloader.eod.accounts.pending", pendingAccountsGauge, AtomicInteger::get).description("Accounts pending in current EOD").register(registry);

        this.activeConnectionsGauge = new AtomicInteger(0);
        Gauge.builder("posloader.connections.active", activeConnectionsGauge, AtomicInteger::get).description("Active MSPM connections").register(registry);

        this.lastEodDurationGauge = new AtomicLong(0);
        Gauge.builder("posloader.eod.last_duration_seconds", lastEodDurationGauge, AtomicLong::get).description("Duration of last EOD run in seconds").register(registry);

        this.lastEodSuccessGauge = new AtomicInteger(0);
        Gauge.builder("posloader.eod.last_success_count", lastEodSuccessGauge, AtomicInteger::get).description("Successful accounts in last EOD").register(registry);

        this.lastEodFailedGauge = new AtomicInteger(0);
        Gauge.builder("posloader.eod.last_failed_count", lastEodFailedGauge, AtomicInteger::get).description("Failed accounts in last EOD").register(registry);

        // Timers
        this.accountProcessingTimer = Timer.builder("posloader.account.processing_time").description("Time to process a single account").publishPercentiles(0.5, 0.75, 0.95, 0.99).publishPercentileHistogram().register(registry);

        this.mspmFetchTimer = Timer.builder("posloader.mspm.fetch_time").description("Time to fetch from MSPM").publishPercentiles(0.5, 0.95, 0.99).register(registry);

        this.dbSaveTimer = Timer.builder("posloader.db.save_time").description("Time to save to database").publishPercentiles(0.5, 0.95, 0.99).register(registry);

        this.validationTimer = Timer.builder("posloader.validation.time").description("Time for position validation").register(registry);

        this.bulkInsertTimer = Timer.builder("posloader.bulk.insert_time").description("Time for bulk insert operations").publishPercentiles(0.5, 0.95).register(registry);

        // Distribution Summaries
        this.positionsPerAccountSummary = DistributionSummary.builder("posloader.positions.per_account").description("Number of positions per account").publishPercentiles(0.5, 0.75, 0.95, 0.99).register(registry);

        this.snapshotSizeSummary = DistributionSummary.builder("posloader.snapshot.size_bytes").description("Snapshot size in bytes").baseUnit("bytes").register(registry);
    }

    public void recordAccountSuccess(Integer accountId, int positionCount, Duration duration) {
        accountsProcessedCounter.increment();
        positionsLoadedCounter.increment(positionCount);
        positionsPerAccountSummary.record(positionCount);
        accountProcessingTimer.record(duration);
    }

    public void recordAccountFailure(Integer accountId, String reason) {
        accountsFailedCounter.increment();
        registry.counter("posloader.account.failure", "reason", sanitizeTag(reason)).increment();
    }

    public void recordMspmFetch(Duration duration, boolean success) {
        mspmCallsCounter.increment();
        mspmFetchTimer.record(duration);
        if (!success) {
            mspmFailuresCounter.increment();
        }
    }

    public void recordValidation(int errors, int warnings, Duration duration) {
        validationTimer.record(duration);
        if (errors > 0) {
            validationErrorsCounter.increment(errors);
        }
        if (warnings > 0) {
            validationWarningsCounter.increment(warnings);
        }
    }

    public void recordValidationFailure(Integer accountId, int errorCount) {
        validationErrorsCounter.increment(errorCount);
        registry.counter("posloader.validation.failures", "account", String.valueOf(accountId)).increment();
    }

    public void recordZeroPriceDetection(Integer accountId, long count) {
        zeroPriceDetectedCounter.increment(count);
        registry.counter("posloader.zero_price.by_account", "account", String.valueOf(accountId)).increment(count);
    }

    public void recordEodCompletion(int success, int failed, Duration duration) {
        lastEodDurationGauge.set(duration.toSeconds());
        lastEodSuccessGauge.set(success);
        lastEodFailedGauge.set(failed);

        registry.counter("posloader.eod.completed", "status", failed > 0 ? "with_errors" : "success").increment();
    }

    public void recordDbSave(int positionCount, Duration duration) {
        dbSaveTimer.record(duration);
        registry.counter("posloader.db.positions_saved").increment(positionCount);
    }

    public void recordBulkInsert(int rowCount, Duration duration) {
        bulkInsertTimer.record(duration);
        registry.counter("posloader.bulk.rows_inserted").increment(rowCount);
    }

    public void recordCacheHit() {
        cacheHitsCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissesCounter.increment();
    }

    public void updatePendingAccounts(int count) {
        pendingAccountsGauge.set(count);
    }

    public void updateActiveConnections(int count) {
        activeConnectionsGauge.set(count);
    }

    public void recordSnapshotSize(long bytes) {
        snapshotSizeSummary.record(bytes);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(registry.timer(name, tags));
    }

    private String sanitizeTag(String value) {
        if (value == null) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9_]", "_").substring(0, Math.min(value.length(), 50));
    }
}
