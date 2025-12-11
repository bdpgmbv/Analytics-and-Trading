package com.vyshali.positionloader.listener;

import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.health.LoaderHealthIndicator;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.AlertService;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka listener with parallel processing, DLQ support, graceful shutdown, and rate limiting.
 * <p>
 * Phase 1 Enhancements: Graceful shutdown, metrics
 * Phase 2 Enhancements:
 * - #9 Alerting: DLQ threshold alerts
 * - #10 Rate Limiting: Semaphore-based concurrent request limiting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final PositionService service;
    private final DataRepository repo;
    private final MeterRegistry metrics;
    private final LoaderHealthIndicator healthIndicator;
    private final LoaderConfig config;
    private final AlertService alertService;  // Phase 2

    // Phase 1: Graceful shutdown
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // Phase 2 Enhancement #10: Rate Limiting
    private Semaphore rateLimiter;
    private static final int DEFAULT_MAX_CONCURRENT = 20;
    private final AtomicInteger rejectedByRateLimit = new AtomicInteger(0);

    // Executor
    private ExecutorService executor;

    // DLQ alerting threshold
    private static final int DLQ_ALERT_THRESHOLD = 50;

    @PostConstruct
    public void init() {
        // Initialize rate limiter from config or default
        int maxConcurrent = config.parallelThreads() > 0 ? config.parallelThreads() * 2 : DEFAULT_MAX_CONCURRENT;
        rateLimiter = new Semaphore(maxConcurrent);
        log.info("Rate limiter initialized with {} permits", maxConcurrent);

        // Register gauge for available permits
        metrics.gauge("posloader.ratelimit.available_permits", rateLimiter, Semaphore::availablePermits);
    }

    private ExecutorService getExecutor() {
        if (executor == null) {
            int threads = Math.min(config.parallelThreads(), Runtime.getRuntime().availableProcessors());
            executor = Executors.newFixedThreadPool(threads);
            log.info("Initialized EOD executor with {} threads", threads);
        }
        return executor;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRACEFUL SHUTDOWN (Phase 1)
    // ═══════════════════════════════════════════════════════════════════════════

    @PreDestroy
    public void shutdown() {
        log.warn("Shutdown signal received. Active jobs: {}", healthIndicator.getActiveJobCount());
        shuttingDown.set(true);

        int waited = 0;
        while (healthIndicator.getActiveJobCount() > 0 && waited < 25) {
            log.info("Waiting for {} active jobs to complete... ({}s)", healthIndicator.getActiveJobCount(), waited);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            waited++;
        }

        if (healthIndicator.getActiveJobCount() > 0) {
            log.error("Forcing shutdown with {} jobs still active!", healthIndicator.getActiveJobCount());
            metrics.counter("posloader.shutdown.forced").increment();
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate gracefully, forcing...");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Position Loader shutdown complete");
    }

    private boolean shouldRejectWork(String source) {
        if (shuttingDown.get()) {
            log.warn("Rejecting {} batch - shutdown in progress", source);
            metrics.counter("posloader.messages.rejected", "reason", "shutdown").increment();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2 ENHANCEMENT #10: RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Try to acquire a rate limit permit.
     * Returns true if acquired, false if rate limited.
     */
    private boolean tryAcquirePermit(String source) {
        if (!rateLimiter.tryAcquire()) {
            int rejected = rejectedByRateLimit.incrementAndGet();
            metrics.counter("posloader.ratelimit.rejected", "source", source).increment();
            log.warn("Rate limit hit for {} - {} total rejections this period", source, rejected);

            // Alert every 10 rejections
            if (rejected % 10 == 0) {
                alertService.rateLimitHit(source, rejected);
            }

            return false;
        }
        return true;
    }

    private void releasePermit() {
        rateLimiter.release();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD PROCESSING - PARALLEL WITH RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(topics = AppConfig.TOPIC_EOD_TRIGGER, groupId = "positionloader-eod", containerFactory = "batchFactory")
    public void onEod(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        // Phase 1: Check shutdown
        if (shouldRejectWork("EOD")) {
            return;
        }

        // Phase 2: Check rate limit at batch level
        if (!tryAcquirePermit("EOD")) {
            // Don't ack - message will be redelivered
            log.warn("EOD batch rate limited, will retry");
            return;
        }

        log.info("EOD batch received: {} accounts", records.size());
        Timer.Sample batchTimer = Timer.start(metrics);

        healthIndicator.jobStarted();

        try {
            List<Integer> accountIds = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                try {
                    accountIds.add(Integer.parseInt(record.value()));
                } catch (NumberFormatException e) {
                    log.error("Invalid account ID: {}", record.value());
                    metrics.counter("posloader.eod.parse_error").increment();
                }
            }

            metrics.summary("posloader.eod.batch_size").record(accountIds.size());

            // PARALLEL PROCESSING with per-account rate limiting
            List<CompletableFuture<EodResult>> futures = accountIds.stream().map(id -> CompletableFuture.supplyAsync(() -> {
                // Phase 2: Per-account rate limiting
                if (!tryAcquirePermit("EOD_ACCOUNT")) {
                    return new EodResult(id, false, "Rate limited");
                }

                Timer.Sample accountTimer = Timer.start(metrics);
                try {
                    service.processEod(id);
                    accountTimer.stop(metrics.timer("posloader.eod.account_duration", "status", "success"));
                    return new EodResult(id, true, null);
                } catch (Exception e) {
                    accountTimer.stop(metrics.timer("posloader.eod.account_duration", "status", "failed"));
                    return new EodResult(id, false, e.getMessage());
                } finally {
                    releasePermit();
                }
            }, getExecutor())).toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            int success = 0, failed = 0, rateLimited = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    EodResult result = futures.get(i).get();
                    if (result.success) {
                        success++;
                    } else if ("Rate limited".equals(result.error)) {
                        rateLimited++;
                        // Don't save to DLQ - will be retried
                    } else {
                        failed++;
                        repo.saveToDlq(AppConfig.TOPIC_EOD_TRIGGER, result.accountId.toString(), result.accountId.toString(), result.error);
                        metrics.counter("posloader.dlq.saved", "topic", "eod").increment();
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Error getting EOD result", e);
                }
            }

            batchTimer.stop(metrics.timer("posloader.eod.batch_duration"));
            metrics.counter("posloader.eod.batch_success").increment(success);
            metrics.counter("posloader.eod.batch_failed").increment(failed);
            if (rateLimited > 0) {
                metrics.counter("posloader.eod.batch_rate_limited").increment(rateLimited);
            }

            log.info("EOD batch complete: {} success, {} failed, {} rate-limited", success, failed, rateLimited);

        } finally {
            healthIndicator.jobEnded();
            releasePermit();
        }

        ack.acknowledge();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(topics = AppConfig.TOPIC_INTRADAY, groupId = "positionloader-intraday", containerFactory = "batchFactory")
    public void onIntraday(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        if (shouldRejectWork("INTRADAY")) {
            return;
        }

        // Phase 2: Rate limit
        if (!tryAcquirePermit("INTRADAY")) {
            return;
        }

        log.debug("Intraday batch: {} records", records.size());
        Timer.Sample batchTimer = Timer.start(metrics);

        healthIndicator.jobStarted();

        try {
            int processed = 0, failed = 0;

            for (ConsumerRecord<String, String> record : records) {
                try {
                    service.processIntradayJson(record.value());
                    processed++;
                } catch (Exception e) {
                    failed++;
                    repo.saveToDlq(AppConfig.TOPIC_INTRADAY, record.key(), record.value(), e.getMessage());
                    metrics.counter("posloader.dlq.saved", "topic", "intraday").increment();
                    log.error("Intraday failed, saved to DLQ: {}", e.getMessage());
                }
            }

            batchTimer.stop(metrics.timer("posloader.intraday.batch_duration"));
            metrics.counter("posloader.intraday.batch_processed").increment(processed);
            metrics.counter("posloader.intraday.batch_failed").increment(failed);

            log.debug("Intraday: {} processed, {} failed", processed, failed);

        } finally {
            healthIndicator.jobEnded();
            releasePermit();
        }

        ack.acknowledge();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DLQ RETRY JOB (with alerting)
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRateString = "${loader.dlq-retry-interval-ms:300000}")
    public void retryDlq() {
        if (shuttingDown.get()) {
            return;
        }

        // Phase 2: Check DLQ depth and alert if high
        int dlqDepth = repo.getDlqDepth();
        metrics.gauge("posloader.dlq.depth", dlqDepth);

        if (dlqDepth > DLQ_ALERT_THRESHOLD) {
            alertService.dlqThresholdExceeded(dlqDepth, DLQ_ALERT_THRESHOLD);
        }

        List<Map<String, Object>> messages = repo.getDlqMessages(10);
        if (messages.isEmpty()) {
            return;
        }

        log.info("Retrying {} DLQ messages (total depth: {})", messages.size(), dlqDepth);

        for (Map<String, Object> msg : messages) {
            Long id = (Long) msg.get("id");
            String topic = (String) msg.get("topic");
            String payload = (String) msg.get("payload");
            int retryCount = ((Number) msg.get("retry_count")).intValue();

            if (retryCount >= config.dlqMaxRetries()) {
                log.error("DLQ message {} exceeded max retries ({}), skipping", id, config.dlqMaxRetries());
                metrics.counter("posloader.dlq.max_retries_exceeded").increment();
                continue;
            }

            try {
                if (AppConfig.TOPIC_EOD_TRIGGER.equals(topic)) {
                    service.processEod(Integer.parseInt(payload));
                } else if (AppConfig.TOPIC_INTRADAY.equals(topic)) {
                    service.processIntradayJson(payload);
                }
                repo.deleteDlq(id);
                metrics.counter("posloader.dlq.retry_success").increment();
                log.info("DLQ retry success: {}", id);
            } catch (Exception e) {
                repo.incrementDlqRetry(id);
                metrics.counter("posloader.dlq.retry_failed").increment();
                log.warn("DLQ retry failed for {}: {}", id, e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SCHEDULED: Reset rate limit rejection counter
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 60000)  // Every minute
    public void resetRateLimitCounter() {
        int rejected = rejectedByRateLimit.getAndSet(0);
        if (rejected > 0) {
            log.info("Rate limit stats - {} rejections in last minute", rejected);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════════════════

    private record EodResult(Integer accountId, boolean success, String error) {
    }
}