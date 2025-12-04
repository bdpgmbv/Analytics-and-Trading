package com.vyshali.tradefillprocessor.service;

/*
 * 12/03/2025 - 1:14 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatorService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void processExecution(ExecutionReportDTO fill) {
        log.info("PROCESSOR: Received Fill for Order {} - Status: {}", fill.orderId(), fill.status());

        // For this flow, we assume immediate COMPLETE fill
        if ("FILLED".equalsIgnoreCase(fill.status())) {
            publishToPositionLoader(fill);
        }
    }

    private void publishToPositionLoader(ExecutionReportDTO fill) {
        TradeEventDTO event = new TradeEventDTO(fill.accountId(), 100, // Client ID
                List.of(new TradeEventDTO.PositionDetail(null, fill.ticker(), fill.lastQty(), fill.side(), fill.lastPx())));

        log.info("PROCESSOR: Publishing Final Trade to PositionLoader");
        kafkaTemplate.send("MSPA_INTRADAY", fill.accountId().toString(), event);
    }
}