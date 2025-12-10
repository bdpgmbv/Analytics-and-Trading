package com.vyshali.positionloader.listener;

/*
 * 12/10/2025 - 1:02 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka listeners for Position Loader.
 * <p>
 * Two flows:
 * 1. EOD Trigger from MSPM (after market close) - receives account ID
 * 2. Intraday Batches from MSPA (throughout the day) - 1-100 transactions per batch
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final SnapshotService snapshotService;

    /**
     * EOD Trigger from MSPM.
     * Receives: Account ID (string)
     * Action: Fetch full snapshot from MSPM REST API and save to DB
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_EOD_TRIGGER, groupId = KafkaConfig.GROUP_EOD, containerFactory = "eodFactory")
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String accountIdStr = record.value();
        log.info("EOD trigger received: account={}", accountIdStr);

        try {
            Integer accountId = Integer.parseInt(accountIdStr);
            snapshotService.processEod(accountId);
            ack.acknowledge();
            log.info("EOD complete: account={}", accountId);

        } catch (NumberFormatException e) {
            log.error("Invalid account ID in EOD trigger: {}", accountIdStr);
            ack.acknowledge(); // Don't retry bad data

        } catch (Exception e) {
            log.error("EOD failed: account={}, error={}", accountIdStr, e.getMessage());
            throw e; // Goes to DLQ after retries
        }
    }

    /**
     * Intraday Batches from MSPA.
     * Receives: Batch of 1-100 JSON records (AccountSnapshotDTO format)
     * Action: Update positions incrementally
     */
    @KafkaListener(topics = KafkaConfig.TOPIC_INTRADAY, groupId = KafkaConfig.GROUP_INTRADAY, containerFactory = "batchFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Intraday batch received: {} records", records.size());

        int success = 0;
        int failed = 0;

        for (ConsumerRecord<String, String> record : records) {
            try {
                snapshotService.processIntradayRecord(record.value());
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Intraday record failed: offset={}, error={}", record.offset(), e.getMessage());
            }
        }

        log.info("Intraday batch complete: success={}, failed={}", success, failed);
        ack.acknowledge();
    }
}