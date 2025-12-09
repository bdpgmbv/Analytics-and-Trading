package com.vyshali.positionloader.listener;

/*
 * 12/1/25 - 23:03
 * FIXED: Changed eodFactory to correct bean name
 * FIXED: Changed intradayFactory to intradayBatchFactory for batch processing
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.TopicConstants;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataListener {

    private final SnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    /**
     * EOD Trigger Listener
     * Listens for account IDs to trigger EOD processing.
     * <p>
     * FIXED: Using 'eodFactory' which is now defined in KafkaConfig
     */
    @KafkaListener(topics = TopicConstants.TOPIC_EOD_TRIGGER, groupId = TopicConstants.GROUP_EOD, containerFactory = "eodFactory")
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Integer accountId = Integer.parseInt(record.value());
            log.info("Received EOD trigger for account: {}", accountId);

            snapshotService.processEodFromMspm(accountId);

            ack.acknowledge();
            log.info("EOD trigger processed successfully for account: {}", accountId);

        } catch (NumberFormatException e) {
            log.error("Invalid account ID in EOD trigger: {}", record.value(), e);
            ack.acknowledge(); // Acknowledge to avoid reprocessing bad message
        } catch (Exception e) {
            log.error("Failed to process EOD trigger for: {}", record.value(), e);
            // Don't acknowledge - let it go to DLQ via error handler
            throw new RuntimeException("EOD processing failed", e);
        }
    }

    /**
     * Intraday Batch Listener
     * Processes intraday position updates in batches.
     * <p>
     * FIXED: Using 'intradayBatchFactory' which is configured for batch processing
     */
    @KafkaListener(topics = TopicConstants.TOPIC_INTRADAY, groupId = TopicConstants.GROUP_INTRADAY, containerFactory = "intradayBatchFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Received intraday batch with {} records", records.size());

        int successCount = 0;
        int failCount = 0;

        for (var record : records) {
            try {
                AccountSnapshotDTO dto = objectMapper.readValue(record.value(), AccountSnapshotDTO.class);
                snapshotService.processIntradayPayload(dto);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("Batch Failure at offset {}: {}", record.offset(), e.getMessage());
                // Continue processing other records in batch
            }
        }

        log.info("Intraday batch completed: {} success, {} failed", successCount, failCount);

        if (failCount > 0 && successCount == 0) {
            // All failed - don't acknowledge to trigger retry/DLQ
            throw new RuntimeException("Entire batch failed processing");
        }

        // Acknowledge the batch
        ack.acknowledge();
    }
}