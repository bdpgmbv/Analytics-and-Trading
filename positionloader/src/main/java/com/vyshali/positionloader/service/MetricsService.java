package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 2:37 PM
 * @author Vyshali Prabananth Lal
 */

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final MeterRegistry registry;

    // Gauges
    private final AtomicInteger eodAccountsPending = new AtomicInteger(0);
    private final AtomicInteger eodAccountsCompleted = new AtomicInteger(0);
    private final AtomicLong eodLastDurationSeconds = new AtomicLong(0);
    private final AtomicInteger eodLastSuccessCount = new AtomicInteger(0);

    // Counters
    private Counter accountsProcessedSuccess;
    private Counter accountsProcessedFailed;
    private Counter zeroPriceDetected;
    private Counter validationErrors;
    private Counter mspmCalls;
    private Counter mspmFailures;

    // Timers
    private Timer accountProcessingTimer;
    private Timer mspmFetchTimer;
    private Timer dbSaveTimer;

    // Cache metrics
    private Counter cacheHits;
    private Counter cacheMisses;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        // Gauges - match prometheus-alerts.yml
        Gauge.builder("posloader_eod_accounts_pending", eodAccountsPending, AtomicInteger::get).description("Number of accounts pending EOD processing").register(registry);

        Gauge.builder("posloader_eod_accounts_completed", eodAccountsCompleted, AtomicInteger::get).description("Number of accounts completed EOD today").register(registry);

        Gauge.builder("posloader_eod_last_duration_seconds", eodLastDurationSeconds, AtomicLong::get).description("Duration of last EOD run in seconds").register(registry);

        Gauge.builder("posloader_eod_last_success_count", eodLastSuccessCount, AtomicInteger::get).description("Number of successful accounts in last EOD run").register(registry);

        // Counters
        accountsProcessedSuccess = Counter.builder("posloader_accounts_processed_total").tag("status", "success").description("Total accounts processed").register(registry);

        accountsProcessedFailed = Counter.builder("posloader_accounts_processed_total").tag("status", "failed").description("Total accounts failed").register(registry);

        zeroPriceDetected = Counter.builder("posloader_zero_price_detected_total").description("Zero-price positions detected").register(registry);

        validationErrors = Counter.builder("posloader_validation_issues").tag("severity", "error").description("Validation errors detected").register(registry);

        mspmCalls = Counter.builder("posloader_mspm_calls_total").description("Total MSPM API calls").register(registry);

        mspmFailures = Counter.builder("posloader_mspm_failures_total").description("Total MSPM API failures").register(registry);

        cacheHits = Counter.builder("posloader_cache_hits_total").description("Cache hits").register(registry);

        cacheMisses = Counter.builder("posloader_cache_misses_total").description("Cache misses").register(registry);

        // Timers
        accountProcessingTimer = Timer.builder("posloader_account_processing_time_seconds").description("Time to process single account").register(registry);

        mspmFetchTimer = Timer.builder("posloader_mspm_fetch_time_seconds").description("Time to fetch from MSPM").register(registry);

        dbSaveTimer = Timer.builder("posloader_db_save_time_seconds").description("Time to save to database").register(registry);
    }

    // ==================== EOD Metrics ====================

    public void setEodPending(int count) {
        eodAccountsPending.set(count);
    }

    public void setEodCompleted(int count) {
        eodAccountsCompleted.set(count);
    }

    public void recordEodDuration(long seconds) {
        eodLastDurationSeconds.set(seconds);
    }

    public void setEodSuccessCount(int count) {
        eodLastSuccessCount.set(count);
    }

    // ==================== Account Processing ====================

    public void recordAccountSuccess() {
        accountsProcessedSuccess.increment();
    }

    public void recordAccountFailure() {
        accountsProcessedFailed.increment();
    }

    public Timer.Sample startAccountTimer() {
        return Timer.start(registry);
    }

    public void stopAccountTimer(Timer.Sample sample) {
        sample.stop(accountProcessingTimer);
    }

    // ==================== Validation ====================

    public void recordZeroPriceDetected(int count) {
        zeroPriceDetected.increment(count);
    }

    public void recordValidationError() {
        validationErrors.increment();
    }

    // ==================== MSPM ====================

    public Timer.Sample startMspmTimer() {
        mspmCalls.increment();
        return Timer.start(registry);
    }

    public void stopMspmTimer(Timer.Sample sample) {
        sample.stop(mspmFetchTimer);
    }

    public void recordMspmFailure() {
        mspmFailures.increment();
    }

    // ==================== Database ====================

    public Timer.Sample startDbTimer() {
        return Timer.start(registry);
    }

    public void stopDbTimer(Timer.Sample sample) {
        sample.stop(dbSaveTimer);
    }

    // ==================== Cache ====================

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }
}
