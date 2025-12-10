package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:59 PM
 * @author Vyshali Prabananth Lal
 */

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Essential business metrics (10 total).
 * Simplified from 30+ metrics to what actually matters.
 */
@Component
public class Metrics {

    // Counters
    private final Counter accountsProcessed;
    private final Counter accountsFailed;
    private final Counter positionsLoaded;
    private final Counter zeroPriceDetected;
    private final Counter validationErrors;

    // Gauges
    private final AtomicInteger pendingAccounts = new AtomicInteger(0);
    private final AtomicLong lastEodDurationSec = new AtomicLong(0);

    // Timers
    private final Timer accountProcessing;
    private final Timer mspmFetch;
    private final Timer dbSave;

    public Metrics(MeterRegistry registry) {
        // Counters
        accountsProcessed = Counter.builder("eod.accounts.processed").description("Accounts processed successfully").register(registry);

        accountsFailed = Counter.builder("eod.accounts.failed").description("Accounts failed processing").register(registry);

        positionsLoaded = Counter.builder("eod.positions.loaded").description("Total positions loaded").register(registry);

        zeroPriceDetected = Counter.builder("eod.zero_price.detected").description("Zero-price positions detected").register(registry);

        validationErrors = Counter.builder("eod.validation.errors").description("Validation errors").register(registry);

        // Gauges
        Gauge.builder("eod.accounts.pending", pendingAccounts, AtomicInteger::get).description("Accounts pending in current EOD").register(registry);

        Gauge.builder("eod.last_duration_seconds", lastEodDurationSec, AtomicLong::get).description("Duration of last EOD run").register(registry);

        // Timers
        accountProcessing = Timer.builder("eod.account.duration").description("Time to process single account").publishPercentiles(0.5, 0.95, 0.99).register(registry);

        mspmFetch = Timer.builder("mspm.fetch.duration").description("Time to fetch from MSPM").publishPercentiles(0.5, 0.95, 0.99).register(registry);

        dbSave = Timer.builder("db.save.duration").description("Time to save to database").publishPercentiles(0.5, 0.95).register(registry);
    }

    // ==================== RECORDING METHODS ====================

    public void recordAccountSuccess(int positionCount, Duration duration) {
        accountsProcessed.increment();
        positionsLoaded.increment(positionCount);
        accountProcessing.record(duration);
    }

    public void recordAccountFailure() {
        accountsFailed.increment();
    }

    public void recordZeroPrice(int count) {
        zeroPriceDetected.increment(count);
    }

    public void recordValidationError(int count) {
        validationErrors.increment(count);
    }

    public void recordMspmFetch(Duration duration) {
        mspmFetch.record(duration);
    }

    public void recordDbSave(Duration duration) {
        dbSave.record(duration);
    }

    public void recordEodComplete(Duration duration) {
        lastEodDurationSec.set(duration.toSeconds());
    }

    public void setPendingAccounts(int count) {
        pendingAccounts.set(count);
    }
}