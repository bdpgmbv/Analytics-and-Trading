package com.vyshali.common.util;

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Utility for common metrics operations.
 * Simplifies timer and counter usage across services.
 * 
 * Usage:
 * <pre>
 * // Time an operation
 * Result result = MetricsHelper.timed(registry, "operation.name", () -> doWork());
 * 
 * // Time with tags
 * Result result = MetricsHelper.timed(registry, "operation.name", 
 *     Tags.of("account", accountId), () -> doWork());
 * 
 * // Increment counter
 * MetricsHelper.count(registry, "events.processed", Tags.of("type", "EOD"));
 * </pre>
 */
public final class MetricsHelper {

    private static final Logger log = LoggerFactory.getLogger(MetricsHelper.class);

    private MetricsHelper() {}

    // ════════════════════════════════════════════════════════════════════════
    // TIMERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Time a callable operation.
     */
    public static <T> T timed(MeterRegistry registry, String name, Callable<T> action) {
        return timed(registry, name, Tags.empty(), action);
    }

    /**
     * Time a callable operation with tags.
     */
    public static <T> T timed(MeterRegistry registry, String name, Tags tags, Callable<T> action) {
        Timer timer = Timer.builder(name)
                .tags(tags)
                .register(registry);

        Timer.Sample sample = Timer.start(registry);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sample.stop(timer);
        }
    }

    /**
     * Time a supplier operation.
     */
    public static <T> T timed(MeterRegistry registry, String name, Supplier<T> action) {
        return timed(registry, name, Tags.empty(), () -> action.get());
    }

    /**
     * Time a runnable operation.
     */
    public static void timed(MeterRegistry registry, String name, Runnable action) {
        timed(registry, name, Tags.empty(), () -> {
            action.run();
            return null;
        });
    }

    /**
     * Time a runnable with tags.
     */
    public static void timed(MeterRegistry registry, String name, Tags tags, Runnable action) {
        timed(registry, name, tags, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Record a duration directly.
     */
    public static void recordTime(MeterRegistry registry, String name, Duration duration) {
        recordTime(registry, name, Tags.empty(), duration);
    }

    /**
     * Record a duration with tags.
     */
    public static void recordTime(MeterRegistry registry, String name, Tags tags, Duration duration) {
        Timer.builder(name)
                .tags(tags)
                .register(registry)
                .record(duration);
    }

    // ════════════════════════════════════════════════════════════════════════
    // COUNTERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Increment a counter.
     */
    public static void count(MeterRegistry registry, String name) {
        count(registry, name, Tags.empty(), 1);
    }

    /**
     * Increment a counter with tags.
     */
    public static void count(MeterRegistry registry, String name, Tags tags) {
        count(registry, name, tags, 1);
    }

    /**
     * Increment a counter by amount.
     */
    public static void count(MeterRegistry registry, String name, double amount) {
        count(registry, name, Tags.empty(), amount);
    }

    /**
     * Increment a counter with tags by amount.
     */
    public static void count(MeterRegistry registry, String name, Tags tags, double amount) {
        Counter.builder(name)
                .tags(tags)
                .register(registry)
                .increment(amount);
    }

    /**
     * Increment with simple tag key-value.
     */
    public static void count(MeterRegistry registry, String name, String tagKey, String tagValue) {
        count(registry, name, Tags.of(tagKey, tagValue), 1);
    }

    // ════════════════════════════════════════════════════════════════════════
    // GAUGES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Register a gauge.
     */
    public static <T extends Number> void gauge(MeterRegistry registry, String name, 
            Supplier<T> valueSupplier) {
        Gauge.builder(name, valueSupplier, Number::doubleValue)
                .register(registry);
    }

    /**
     * Register a gauge with tags.
     */
    public static <T extends Number> void gauge(MeterRegistry registry, String name, 
            Tags tags, Supplier<T> valueSupplier) {
        Gauge.builder(name, valueSupplier, Number::doubleValue)
                .tags(tags)
                .register(registry);
    }

    // ════════════════════════════════════════════════════════════════════════
    // DISTRIBUTION SUMMARIES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Record a distribution value.
     */
    public static void recordValue(MeterRegistry registry, String name, double value) {
        recordValue(registry, name, Tags.empty(), value);
    }

    /**
     * Record a distribution value with tags.
     */
    public static void recordValue(MeterRegistry registry, String name, Tags tags, double value) {
        DistributionSummary.builder(name)
                .tags(tags)
                .register(registry)
                .record(value);
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Create success/failure tags.
     */
    public static Tags successTags() {
        return Tags.of("status", "success");
    }

    public static Tags failureTags() {
        return Tags.of("status", "failed");
    }

    public static Tags statusTags(boolean success) {
        return success ? successTags() : failureTags();
    }

    /**
     * Create account tags.
     */
    public static Tags accountTags(int accountId) {
        return Tags.of("accountId", String.valueOf(accountId));
    }

    /**
     * Create topic tags.
     */
    public static Tags topicTags(String topic) {
        return Tags.of("topic", topic);
    }

    /**
     * Combine tags.
     */
    public static Tags combine(Tags... tagArrays) {
        Tags result = Tags.empty();
        for (Tags tags : tagArrays) {
            result = result.and(tags);
        }
        return result;
    }
}
