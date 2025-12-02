package com.vyshali.positionloader.listener;

/*
 * 12/1/25 - 23:03
 * @author Vyshali Prabananth Lal
 */

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

    @KafkaListener(topics = "MSPM_EOD_TRIGGER", groupId = "eod-group", containerFactory = "singleFactory")
    public void onEodTrigger(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            snapshotService.processEodFromMspm(Integer.parseInt(record.value()));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("EOD Failed: {}", record.value(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "MSPA_INTRADAY", groupId = "intra-group", containerFactory = "batchFactory")
    public void onIntradayBatch(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (var record : records) {
            try {
                snapshotService.processIntradayFromMspa(Integer.parseInt(record.value()));
            } catch (Exception e) {
                log.error("Intraday Failed: {}", record.value(), e);
            }
        }
        ack.acknowledge();
    }
}
