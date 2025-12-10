package com.vyshali.positionloader.listener;

/*
 * 12/10/2025 - 1:02 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.repository.TransactionRepository;
import com.vyshali.positionloader.service.SnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * All Kafka listeners in one place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final SnapshotService snapshotService;
    private final TransactionRepository transactions;
    private final ObjectMapper json;

    // ==================== EOD TRIGGER ====================

    @KafkaListener(topics = KafkaConfig.TOPIC_EOD_TRIGGER, groupId = KafkaConfig.GROUP_EOD, containerFactory = "eodFactory")
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Integer accountId = Integer.parseInt(record.value());
            log.info("EOD trigger for account {}", accountId);

            snapshotService.processEod(accountId);

            ack.acknowledge();
            log.info("EOD complete for account {}", accountId);

        } catch (NumberFormatException e) {
            log.error("Invalid account ID: {}", record.value());
            ack.acknowledge(); // Don't retry bad data
        } catch (Exception e) {
            log.error("EOD failed for {}: {}", record.value(), e.getMessage());
            throw new RuntimeException("EOD failed", e); // Goes to DLQ
        }
    }

    // ==================== INTRADAY BATCH ====================

    @KafkaListener(topics = KafkaConfig.TOPIC_INTRADAY, groupId = KafkaConfig.GROUP_INTRADAY, containerFactory = "batchFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("Intraday batch: {} records", records.size());

        int success = 0, failed = 0;

        for (var record : records) {
            try {
                AccountSnapshotDTO dto = json.readValue(record.value(), AccountSnapshotDTO.class);
                snapshotService.processIntraday(dto);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("Batch item failed at offset {}: {}", record.offset(), e.getMessage());
            }
        }

        log.info("Intraday batch: {} success, {} failed", success, failed);

        if (failed > 0 && success == 0) {
            throw new RuntimeException("Entire batch failed"); // Goes to DLQ
        }

        ack.acknowledge();
    }

    // ==================== TRADE EVENTS ====================

    @KafkaListener(topics = KafkaConfig.TOPIC_INTRADAY, groupId = "positionloader-trades", containerFactory = "tradeFactory")
    public void onTradeEvent(@Payload @Valid TradeEventDTO event) {
        log.info("Trade event: {} for account {}", event.transactionId(), event.accountId());

        // Idempotency check
        if (transactions.exists(event.transactionId())) {
            log.warn("Duplicate trade {}, skipping", event.transactionId());
            return;
        }

        snapshotService.processTrade(event);
    }
}