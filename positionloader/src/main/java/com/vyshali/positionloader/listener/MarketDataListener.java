package com.vyshali.positionloader.listener;

/*
 * 12/1/25 - 23:03
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.TopicConstants; // Import Constants
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

    @KafkaListener(topics = TopicConstants.TOPIC_EOD_TRIGGER, groupId = TopicConstants.GROUP_EOD, containerFactory = "eodFactory")
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        snapshotService.processEodFromMspm(Integer.parseInt(record.value()));
        ack.acknowledge();
    }

    @KafkaListener(topics = TopicConstants.TOPIC_INTRADAY, groupId = TopicConstants.GROUP_INTRADAY, containerFactory = "intradayFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                // Records work natively with Jackson in Spring Boot 3.2+
                AccountSnapshotDTO dto = objectMapper.readValue(record.value(), AccountSnapshotDTO.class);
                snapshotService.processIntradayPayload(dto);
            } catch (Exception e) {
                log.error("Batch Failure offset {}: {}", record.offset(), e.getMessage());
                throw new RuntimeException("Batch Failed", e);
            }
        }
        ack.acknowledge();
    }
}