package com.vyshali.positionloader.listener;

/*
 * 12/04/2025 - 10:48 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.service.SnapshotService;
import jakarta.validation.Valid; // Import this
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntradayListener {

    private final SnapshotService snapshotService;

    @KafkaListener(topics = "MSPA_INTRADAY", groupId = "loader-group")
    public void onTradeEvent(@Payload @Valid TradeEventDTO event) {
        // @Valid checks the DTO before this method runs.
        // If invalid, it throws an exception and the message goes to the DLQ (handled by framework).
        log.info("Received Validated Trade Event: {}", event.transactionId());
        snapshotService.processTradeEvent(event);
    }
}