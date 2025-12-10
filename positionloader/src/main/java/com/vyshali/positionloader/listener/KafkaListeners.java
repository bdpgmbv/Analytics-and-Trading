package com.vyshali.positionloader.listener;

/*
 * FINAL VERSION: Batch mode + Sharding + Graceful Shutdown
 *
 * Features:
 * - Batch processing for both EOD and Intraday
 * - Sharding for horizontal scaling
 * - Graceful shutdown handling
 * - Parallel EOD processing
 */

import com.vyshali.positionloader.config.GracefulShutdownConfig;
import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.service.ShardingService;
import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final SnapshotService snapshotService;
    private final ShardingService shardingService;

    private final ExecutorService eodExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * EOD batch processing with sharding and graceful shutdown.
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_EOD_TRIGGER, groupId = "positionloader-eod-group", containerFactory = "batchFactory")
    public void onEodTriggerBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {

        // ═══════════════════════════════════════════════════════════════
        // GRACEFUL SHUTDOWN: Check if we should accept new work
        // ═══════════════════════════════════════════════════════════════
        if (GracefulShutdownConfig.isShuttingDown()) {
            log.warn("Shutdown in progress, skipping EOD batch of {} records", records.size());
            ack.acknowledge();
            return;
        }

        // Register this operation
        if (!GracefulShutdownConfig.startOperation()) {
            log.warn("Could not start operation during shutdown");
            ack.acknowledge();
            return;
        }

        try {
            processEodBatch(records, ack);
        } finally {
            // Always mark operation complete
            GracefulShutdownConfig.endOperation();
        }
    }

    private void processEodBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("EOD batch received: {} accounts", records.size());
        long startTime = System.currentTimeMillis();

        // Extract valid account IDs
        List<Integer> allAccountIds = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            try {
                allAccountIds.add(Integer.parseInt(record.value()));
            } catch (NumberFormatException e) {
                log.error("Invalid account ID: {}", record.value());
            }
        }

        // Filter by shard
        List<Integer> myAccountIds = shardingService.filterMyAccounts(allAccountIds);

        if (shardingService.isShardingEnabled()) {
            log.info("Shard {}/{}: processing {} of {} accounts", shardingService.getShardIndex(), shardingService.getTotalShards(), myAccountIds.size(), allAccountIds.size());
        }

        if (myAccountIds.isEmpty()) {
            ack.acknowledge();
            return;
        }

        // Process in parallel
        List<CompletableFuture<EodResult>> futures = myAccountIds.stream().map(accountId -> CompletableFuture.supplyAsync(() -> {
            // Check shutdown before each account
            if (GracefulShutdownConfig.isShuttingDown()) {
                return new EodResult(accountId, false, "Shutdown in progress");
            }
            try {
                snapshotService.processEod(accountId);
                return new EodResult(accountId, true, null);
            } catch (Exception e) {
                log.error("EOD failed for account {}: {}", accountId, e.getMessage());
                return new EodResult(accountId, false, e.getMessage());
            }
        }, eodExecutor)).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Count results
        int success = 0, failed = 0;
        List<Integer> failedAccounts = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            try {
                EodResult result = futures.get(i).get();
                if (result.success) success++;
                else {
                    failed++;
                    failedAccounts.add(result.accountId);
                }
            } catch (Exception e) {
                failed++;
                failedAccounts.add(myAccountIds.get(i));
            }
        }

        log.info("EOD complete: {} success, {} failed, {}ms", success, failed, System.currentTimeMillis() - startTime);

        if (!failedAccounts.isEmpty()) {
            log.error("Failed accounts: {}", failedAccounts);
        }

        ack.acknowledge();
    }

    /**
     * Intraday batch processing with sharding and graceful shutdown.
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_INTRADAY, groupId = "positionloader-intraday-group", containerFactory = "batchFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {

        // Graceful shutdown check
        if (GracefulShutdownConfig.isShuttingDown()) {
            log.warn("Shutdown in progress, skipping intraday batch");
            ack.acknowledge();
            return;
        }

        if (!GracefulShutdownConfig.startOperation()) {
            ack.acknowledge();
            return;
        }

        try {
            int processed = 0, skipped = 0, failed = 0;

            for (ConsumerRecord<String, String> record : records) {
                // Check shutdown during processing
                if (GracefulShutdownConfig.isShuttingDown()) {
                    log.warn("Shutdown during intraday batch, {} remaining", records.size() - processed - skipped - failed);
                    break;
                }

                try {
                    Integer accountId = extractAccountId(record.value());

                    if (!shardingService.isMyAccount(accountId)) {
                        skipped++;
                        continue;
                    }

                    snapshotService.processIntradayRecord(record.value());
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.error("Intraday failed: {}", e.getMessage());
                }
            }

            log.info("Intraday: processed={}, skipped={}, failed={}", processed, skipped, failed);
        } finally {
            GracefulShutdownConfig.endOperation();
        }

        ack.acknowledge();
    }

    private Integer extractAccountId(String json) {
        int start = json.indexOf("\"accountId\"");
        if (start == -1) throw new IllegalArgumentException("No accountId in record");
        int colonIndex = json.indexOf(":", start);
        int endIndex = json.indexOf(",", colonIndex);
        if (endIndex == -1) endIndex = json.indexOf("}", colonIndex);
        return Integer.parseInt(json.substring(colonIndex + 1, endIndex).trim());
    }

    private record EodResult(Integer accountId, boolean success, String error) {
    }
}