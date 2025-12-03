package com.vyshali.mockupstream.listener;

/*
 * 12/03/2025 - 1:54 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.service.KafkaPublisherService;
import com.vyshali.mockupstream.dto.ExecutionReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxMatrixListener {

    private final KafkaPublisherService publisher;

    // Listens for orders sent by HedgeService
    @KafkaListener(topics = "FX_MATRIX_ORDERS", groupId = "mock-matrix-group")
    public void onHedgeOrder(ConsumerRecord<String, String> record) {
        String clOrdId = record.key();
        log.info("FX MATRIX received Order ID: {}. Processing...", clOrdId);

        // 1. Simulate Latency
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }

        // 2. Create Dummy Execution Report (Simulating a Fill)
        // In a real mock, you would parse the JSON value to match Qty/Ticker
        ExecutionReportDTO fill = new ExecutionReportDTO(clOrdId, "EXEC-MX-" + UUID.randomUUID().toString().substring(0, 6), 1001, // Dummy Account
                "EURUSD", // Dummy Ticker
                "BUY", new BigDecimal("100000"), // Dummy Qty
                new BigDecimal("1.0550"), // Execution Price
                "FILLED", "FX_MATRIX_PROD");

        // 3. Send back to TradeFillProcessor
        publisher.sendExecutionReport(fill);
        log.info("FX MATRIX executed Order {}. Fill sent to TradeFillProcessor.", clOrdId);
    }
}
