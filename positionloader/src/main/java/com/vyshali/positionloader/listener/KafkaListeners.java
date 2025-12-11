package com.vyshali.positionloader.listener;

import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.repository.DataRepository;
import com.vyshali.positionloader.service.PositionService;
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

/**
 * Kafka listener with parallel processing and DLQ support.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final PositionService service;
    private final DataRepository repo;

    // Parallel executor for EOD processing (8 threads)
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.min(8, Runtime.getRuntime().availableProcessors()));

    /**
     * EOD batch processing - PARALLEL for performance
     */
    @KafkaListener(topics = AppConfig.TOPIC_EOD_TRIGGER, groupId = "positionloader-eod", containerFactory = "batchFactory")
    public void onEod(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("EOD batch: {} accounts", records.size());
        long start = System.currentTimeMillis();

        // Parse account IDs
        List<Integer> accountIds = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            try {
                accountIds.add(Integer.parseInt(record.value()));
            } catch (NumberFormatException e) {
                log.error("Invalid account ID: {}", record.value());
            }
        }

        // PARALLEL PROCESSING - 8x faster
        List<CompletableFuture<EodResult>> futures = accountIds.stream().map(id -> CompletableFuture.supplyAsync(() -> {
            try {
                service.processEod(id);
                return new EodResult(id, true, null);
            } catch (Exception e) {
                return new EodResult(id, false, e.getMessage());
            }
        }, executor)).toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Count results and save failures to DLQ
        int success = 0, failed = 0;
        for (int i = 0; i < futures.size(); i++) {
            try {
                EodResult result = futures.get(i).get();
                if (result.success) {
                    success++;
                } else {
                    failed++;
                    // Save to DLQ for retry
                    repo.saveToDlq(AppConfig.TOPIC_EOD_TRIGGER, result.accountId.toString(), result.accountId.toString(), result.error);
                }
            } catch (Exception e) {
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("EOD complete: {} success, {} failed, {}ms", success, failed, duration);

        ack.acknowledge();
    }

    /**
     * Intraday processing with DLQ
     */
    @KafkaListener(topics = AppConfig.TOPIC_INTRADAY, groupId = "positionloader-intraday", containerFactory = "batchFactory")
    public void onIntraday(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.debug("Intraday batch: {} records", records.size());

        int processed = 0, failed = 0;
        for (ConsumerRecord<String, String> record : records) {
            try {
                service.processIntradayJson(record.value());
                processed++;
            } catch (Exception e) {
                failed++;
                // Save to DLQ
                repo.saveToDlq(AppConfig.TOPIC_INTRADAY, record.key(), record.value(), e.getMessage());
                log.error("Intraday failed, saved to DLQ: {}", e.getMessage());
            }
        }

        log.debug("Intraday: {} processed, {} failed", processed, failed);
        ack.acknowledge();
    }

    /**
     * DLQ retry job - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void retryDlq() {
        List<Map<String, Object>> messages = repo.getDlqMessages(10);
        if (messages.isEmpty()) return;

        log.info("Retrying {} DLQ messages", messages.size());

        for (Map<String, Object> msg : messages) {
            Long id = (Long) msg.get("id");
            String topic = (String) msg.get("topic");
            String payload = (String) msg.get("payload");

            try {
                if (AppConfig.TOPIC_EOD_TRIGGER.equals(topic)) {
                    service.processEod(Integer.parseInt(payload));
                } else if (AppConfig.TOPIC_INTRADAY.equals(topic)) {
                    service.processIntradayJson(payload);
                }
                repo.deleteDlq(id);
                log.info("DLQ retry success: {}", id);
            } catch (Exception e) {
                repo.incrementDlqRetry(id);
                log.warn("DLQ retry failed: {}", e.getMessage());
            }
        }
    }

    private record EodResult(Integer accountId, boolean success, String error) {
    }
}