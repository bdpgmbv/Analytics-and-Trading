package com.vyshali.positionloader.listener;

import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.health.LoaderHealthIndicator;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.AlertService;
import com.vyshali.positionloader.service.PositionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka listener with parallel processing, DLQ, graceful shutdown, rate limiting,
 * consumer lag monitoring, and request tracing.
 * <p>
 * Phase 1: Graceful shutdown, metrics
 * Phase 2: Alerting, rate limiting
 * Phase 3: Consumer lag monitoring (#12), Request tracing (#13)
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
    private final AlertService alertService;
    private final Tracer tracer;  // Phase 3: Tracing

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // Shutdown flag
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // Rate limiting
    private Semaphore rateLimiter;
    private static final int DEFAULT_MAX_CONCURRENT = 20;
    private final AtomicInteger rejectedByRateLimit = new AtomicInteger(0);

    // Executor
    private ExecutorService executor;

    // Consumer lag monitoring
    private AdminClient adminClient;
    private static final String EOD_CONSUMER_GROUP = "positionloader-eod";
    private static final String INTRADAY_CONSUMER_GROUP = "positionloader-intraday";
    private static final long LAG_ALERT_THRESHOLD = 1000;

    @PostConstruct
    public void init() {
        int maxConcurrent = config.parallelThreads() > 0 ? config.parallelThreads() * 2 : DEFAULT_MAX_CONCURRENT;
        rateLimiter = new Semaphore(maxConcurrent);
        log.info("Rate limiter initialized with {} permits", maxConcurrent);
        metrics.gauge("posloader.ratelimit.available_permits", rateLimiter, Semaphore::availablePermits);

        // Initialize admin client for lag monitoring
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            adminClient = AdminClient.create(props);
            log.info("Kafka AdminClient initialized for lag monitoring");
        } catch (Exception e) {
            log.warn("Failed to initialize AdminClient for lag monitoring: {}", e.getMessage());
        }
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
    // PHASE 3 ENHANCEMENT #12: CONSUMER LAG MONITORING
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 60000)  // Every minute
    public void checkConsumerLag() {
        if (adminClient == null || shuttingDown.get()) return;

        checkLagForGroup(EOD_CONSUMER_GROUP, AppConfig.TOPIC_EOD_TRIGGER);
        checkLagForGroup(INTRADAY_CONSUMER_GROUP, AppConfig.TOPIC_INTRADAY);
    }

    private void checkLagForGroup(String groupId, String topic) {
        try {
            // Get committed offsets
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            // Get end offsets
            Set<TopicPartition> partitions = new HashSet<>();
            for (TopicPartition tp : offsets.keySet()) {
                if (tp.topic().equals(topic)) {
                    partitions.add(tp);
                }
            }

            if (partitions.isEmpty()) {
                return;  // No partitions for this topic
            }

            Map<TopicPartition, Long> endOffsets = adminClient.listOffsets(partitions.stream().collect(java.util.stream.Collectors.toMap(tp -> tp, tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()))).all().get(5, TimeUnit.SECONDS).entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

            // Calculate total lag
            long totalLag = 0;
            for (TopicPartition tp : partitions) {
                OffsetAndMetadata committed = offsets.get(tp);
                Long end = endOffsets.get(tp);
                if (committed != null && end != null) {
                    long lag = end - committed.offset();
                    totalLag += Math.max(0, lag);
                }
            }

            // Record metric
            metrics.gauge("posloader.consumer.lag", io.micrometer.core.instrument.Tags.of("group", groupId, "topic", topic), totalLag);

            // Alert if lag is high
            if (totalLag > LAG_ALERT_THRESHOLD) {
                log.warn("High consumer lag for {}/{}: {} messages behind", groupId, topic, totalLag);
                alertService.warning("CONSUMER_LAG_HIGH", String.format("Consumer group %s is %d messages behind on %s", groupId, totalLag, topic), groupId);
            }

            log.debug("Consumer lag for {}/{}: {}", groupId, topic, totalLag);

        } catch (Exception e) {
            log.debug("Failed to check consumer lag for {}: {}", groupId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRACEFUL SHUTDOWN
    // ═══════════════════════════════════════════════════════════════════════════

    @PreDestroy
    public void shutdown() {
        log.warn("Shutdown signal received. Active jobs: {}", healthIndicator.getActiveJobCount());
        shuttingDown.set(true);

        int waited = 0;
        while (healthIndicator.getActiveJobCount() > 0 && waited < 25) {
            log.info("Waiting for {} active jobs... ({}s)", healthIndicator.getActiveJobCount(), waited);
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
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (adminClient != null) {
            adminClient.close();
        }

        log.info("Position Loader shutdown complete");
    }

    private boolean shouldRejectWork(String source) {
        if (shuttingDown.get()) {
            log.warn("Rejecting {} - shutdown in progress", source);
            metrics.counter("posloader.messages.rejected", "reason", "shutdown").increment();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RATE LIMITING
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean tryAcquirePermit(String source) {
        if (!rateLimiter.tryAcquire()) {
            int rejected = rejectedByRateLimit.incrementAndGet();
            metrics.counter("posloader.ratelimit.rejected", "source", source).increment();
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
    // EOD PROCESSING WITH TRACING (Phase 3 #13)
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(topics = AppConfig.TOPIC_EOD_TRIGGER, groupId = EOD_CONSUMER_GROUP, containerFactory = "batchFactory")
    public void onEod(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        if (shouldRejectWork("EOD")) return;
        if (!tryAcquirePermit("EOD")) return;

        // Phase 3: Create trace span for entire batch
        Span batchSpan = tracer.nextSpan().name("eod-batch-processing").start();

        try (var scope = tracer.withSpan(batchSpan)) {
            batchSpan.tag("batch.size", String.valueOf(records.size()));
            batchSpan.event("batch_received");

            log.info("EOD batch received: {} accounts [traceId={}]", records.size(), batchSpan.context().traceId());

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
                batchSpan.event("parsing_complete");

                // Parallel processing with per-account spans
                List<CompletableFuture<EodResult>> futures = accountIds.stream().map(id -> CompletableFuture.supplyAsync(() -> processEodWithTracing(id, batchSpan), getExecutor())).toList();

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                batchSpan.event("all_accounts_processed");

                int success = 0, failed = 0, rateLimited = 0;
                for (CompletableFuture<EodResult> future : futures) {
                    try {
                        EodResult result = future.get();
                        if (result.success) success++;
                        else if ("Rate limited".equals(result.error)) rateLimited++;
                        else {
                            failed++;
                            repo.saveToDlq(AppConfig.TOPIC_EOD_TRIGGER, result.accountId.toString(), result.accountId.toString(), result.error);
                        }
                    } catch (Exception e) {
                        failed++;
                    }
                }

                batchSpan.tag("batch.success", String.valueOf(success));
                batchSpan.tag("batch.failed", String.valueOf(failed));

                batchTimer.stop(metrics.timer("posloader.eod.batch_duration"));
                metrics.counter("posloader.eod.batch_success").increment(success);
                metrics.counter("posloader.eod.batch_failed").increment(failed);

                log.info("EOD batch complete: {} success, {} failed, {} rate-limited", success, failed, rateLimited);

            } finally {
                healthIndicator.jobEnded();
                releasePermit();
            }

            ack.acknowledge();
            batchSpan.event("acknowledged");

        } finally {
            batchSpan.end();
        }
    }

    /**
     * Process single account EOD with its own trace span.
     */
    private EodResult processEodWithTracing(Integer accountId, Span parentSpan) {
        if (!tryAcquirePermit("EOD_ACCOUNT")) {
            return new EodResult(accountId, false, "Rate limited");
        }

        // Create child span for this account
        Span accountSpan = tracer.nextSpan(parentSpan).name("eod-account-processing").start();

        try (var scope = tracer.withSpan(accountSpan)) {
            accountSpan.tag("account.id", accountId.toString());
            accountSpan.event("processing_start");

            Timer.Sample accountTimer = Timer.start(metrics);
            try {
                service.processEod(accountId);
                accountSpan.event("processing_complete");
                accountTimer.stop(metrics.timer("posloader.eod.account_duration", "status", "success"));
                return new EodResult(accountId, true, null);
            } catch (Exception e) {
                accountSpan.tag("error", e.getMessage());
                accountSpan.event("processing_failed");
                accountTimer.stop(metrics.timer("posloader.eod.account_duration", "status", "failed"));
                return new EodResult(accountId, false, e.getMessage());
            }
        } finally {
            releasePermit();
            accountSpan.end();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTRADAY PROCESSING WITH TRACING
    // ═══════════════════════════════════════════════════════════════════════════

    @KafkaListener(topics = AppConfig.TOPIC_INTRADAY, groupId = INTRADAY_CONSUMER_GROUP, containerFactory = "batchFactory")
    public void onIntraday(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        if (shouldRejectWork("INTRADAY")) return;
        if (!tryAcquirePermit("INTRADAY")) return;

        Span batchSpan = tracer.nextSpan().name("intraday-batch-processing").start();

        try (var scope = tracer.withSpan(batchSpan)) {
            batchSpan.tag("batch.size", String.valueOf(records.size()));

            log.debug("Intraday batch: {} records", records.size());
            Timer.Sample batchTimer = Timer.start(metrics);
            healthIndicator.jobStarted();

            try {
                int processed = 0, failed = 0;

                for (ConsumerRecord<String, String> record : records) {
                    Span recordSpan = tracer.nextSpan(batchSpan).name("intraday-record").start();
                    try (var recordScope = tracer.withSpan(recordSpan)) {
                        service.processIntradayJson(record.value());
                        processed++;
                        recordSpan.event("success");
                    } catch (Exception e) {
                        failed++;
                        recordSpan.tag("error", e.getMessage());
                        repo.saveToDlq(AppConfig.TOPIC_INTRADAY, record.key(), record.value(), e.getMessage());
                    } finally {
                        recordSpan.end();
                    }
                }

                batchTimer.stop(metrics.timer("posloader.intraday.batch_duration"));
                metrics.counter("posloader.intraday.batch_processed").increment(processed);
                metrics.counter("posloader.intraday.batch_failed").increment(failed);

            } finally {
                healthIndicator.jobEnded();
                releasePermit();
            }

            ack.acknowledge();
        } finally {
            batchSpan.end();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DLQ RETRY
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRateString = "${loader.dlq-retry-interval-ms:300000}")
    public void retryDlq() {
        if (shuttingDown.get()) return;

        int dlqDepth = repo.getDlqDepth();
        metrics.gauge("posloader.dlq.depth", dlqDepth);

        if (dlqDepth > 50) {
            alertService.dlqThresholdExceeded(dlqDepth, 50);
        }

        List<Map<String, Object>> messages = repo.getDlqMessages(10);
        if (messages.isEmpty()) return;

        log.info("Retrying {} DLQ messages", messages.size());

        for (Map<String, Object> msg : messages) {
            Long id = (Long) msg.get("id");
            String topic = (String) msg.get("topic");
            String payload = (String) msg.get("payload");
            int retryCount = ((Number) msg.get("retry_count")).intValue();

            if (retryCount >= config.dlqMaxRetries()) {
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
            } catch (Exception e) {
                repo.incrementDlqRetry(id);
                metrics.counter("posloader.dlq.retry_failed").increment();
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void resetRateLimitCounter() {
        int rejected = rejectedByRateLimit.getAndSet(0);
        if (rejected > 0) {
            log.info("Rate limit stats - {} rejections in last minute", rejected);
        }
    }

    private record EodResult(Integer accountId, boolean success, String error) {
    }
}