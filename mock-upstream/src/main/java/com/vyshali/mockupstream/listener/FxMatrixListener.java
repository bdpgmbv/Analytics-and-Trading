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

    // Step 2: Listen for the Order
    @KafkaListener(topics = "FX_MATRIX_ORDERS", groupId = "mock-matrix-group")
    public void onHedgeOrder(ConsumerRecord<String, String> record) {
        String clOrdId = record.key();
        log.info("EXCHANGE: Received Order {}", clOrdId);

        // Simulate processing time
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }

        // Step 3: Generate a FILL (Execution Report)
        // Hardcoded for demo: Buying 1000 EURUSD
        ExecutionReportDTO fill = new ExecutionReportDTO(clOrdId, "EXEC-" + UUID.randomUUID().toString().substring(0, 6), 1001,       // Hardcoded Account for demo
                "EURUSD", "BUY", new BigDecimal("1000"), // Qty Filled
                new BigDecimal("1.0550"), // Price
                "FILLED", "FX_MATRIX");

        // Send back to TradeFillProcessor
        publisher.sendExecutionReport(fill);
        log.info("EXCHANGE: Filled Order {}", clOrdId);
    }
}