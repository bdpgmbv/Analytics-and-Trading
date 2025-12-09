package com.vyshali.positionloader.listener;

/*
 * 12/09/2025 - Added Idempotency Logic
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.repository.TransactionRepository;
import com.vyshali.positionloader.service.SnapshotService;
import jakarta.validation.Valid;
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
    private final TransactionRepository txnRepo;

    @KafkaListener(topics = "MSPA_INTRADAY", groupId = "loader-group", containerFactory = "intradayFactory")
    public void onTradeEvent(@Payload @Valid TradeEventDTO event) {
        log.info("Received Trade Event: {}", event.transactionId());

        // *** IDEMPOTENCY CHECK ***
        // If we have already seen this Transaction ID, ignore it.
        if (txnRepo.existsByTransactionId(event.transactionId())) {
            log.warn("DUPLICATE DETECTED: Trade {} has already been processed. Skipping.", event.transactionId());
            return;
        }

        snapshotService.processTradeEvent(event);
    }
}