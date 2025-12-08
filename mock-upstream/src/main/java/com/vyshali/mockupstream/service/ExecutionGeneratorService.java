package com.vyshali.mockupstream.service;

/*
 * 12/03/2025 - 6:08 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.dto.ExecutionReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionGeneratorService {

    private final KafkaPublisherService publisher;

    @Async
    public void simulateTrade(Integer accountId, String ticker, int totalQty, double price) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Simulating Order {} for {} shares of {}", orderId, totalQty, ticker);

        int[] fills = {(int) (totalQty * 0.3), (int) (totalQty * 0.5), (int) (totalQty * 0.2)};
        double[] prices = {price - 0.05, price + 0.10, price};

        for (int i = 0; i < fills.length; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // FIXED
                log.warn("Simulation interrupted");
                return;
            }
            boolean isLast = (i == fills.length - 1);
            ExecutionReportDTO report = new ExecutionReportDTO(orderId, "EXEC-" + System.nanoTime(), accountId, ticker, "BUY", BigDecimal.valueOf(fills[i]), BigDecimal.valueOf(prices[i]), isLast ? "FILLED" : "PARTIALLY_FILLED", "NYSE");
            publisher.sendExecutionReport(report);
        }
    }
}