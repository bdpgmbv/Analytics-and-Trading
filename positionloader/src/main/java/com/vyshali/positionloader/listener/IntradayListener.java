package com.vyshali.positionloader.listener;

/*
 * 12/04/2025 - 10:48 AM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntradayListener {

    private final SnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "MSPA_INTRADAY", groupId = "loader-group")
    public void onTradeEvent(ConsumerRecord<String, String> record) {
        try {
            TradeEventDTO trade = objectMapper.readValue(record.value(), TradeEventDTO.class);
            log.info("LOADER: Received Trade for Account {}. Updating DB...", trade.accountId());
            snapshotService.processTradeEvent(trade);
        } catch (Exception e) {
            log.error("Error processing trade event", e);
        }
    }
}
