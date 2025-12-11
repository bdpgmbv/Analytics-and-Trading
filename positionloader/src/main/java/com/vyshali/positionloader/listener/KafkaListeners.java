package com.vyshali.positionloader.listener;

import com.vyshali.positionloader.config.AppConfig;
import com.vyshali.positionloader.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple Kafka listener for EOD and Intraday messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaListeners {

    private final PositionService service;

    @KafkaListener(topics = AppConfig.TOPIC_EOD_TRIGGER, groupId = "positionloader-eod", containerFactory = "batchFactory")
    public void onEod(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.info("EOD batch: {} accounts", records.size());

        int success = 0, failed = 0;
        for (ConsumerRecord<String, String> record : records) {
            try {
                Integer accountId = Integer.parseInt(record.value());
                service.processEod(accountId);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("EOD failed: {}", e.getMessage());
            }
        }

        log.info("EOD batch complete: {} success, {} failed", success, failed);
        ack.acknowledge();
    }

    @KafkaListener(topics = AppConfig.TOPIC_INTRADAY, groupId = "positionloader-intraday", containerFactory = "batchFactory")
    public void onIntraday(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        log.debug("Intraday batch: {} records", records.size());

        int processed = 0;
        for (ConsumerRecord<String, String> record : records) {
            try {
                service.processIntradayJson(record.value());
                processed++;
            } catch (Exception e) {
                log.error("Intraday failed: {}", e.getMessage());
            }
        }

        log.debug("Intraday processed: {}", processed);
        ack.acknowledge();
    }
}