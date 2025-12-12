package com.vyshali.tradefillprocessor.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled job to detect and handle stale pending trades.
 * 
 * Trades that have been SENT but not received a response within
 * the timeout period are marked as FAILED.
 */
@Slf4j
@Component
public class StaleTradeChecker {

    private final TradeExecutionService tradeExecutionService;

    @Value("${fxanalyzer.trade.execution-timeout-minutes:5}")
    private int executionTimeoutMinutes;

    private final AtomicInteger stalePendingCount = new AtomicInteger(0);

    public StaleTradeChecker(TradeExecutionService tradeExecutionService, MeterRegistry meterRegistry) {
        this.tradeExecutionService = tradeExecutionService;

        // Register gauge for stale pending trades
        Gauge.builder("trade.pending.stale.count", stalePendingCount, AtomicInteger::get)
                .description("Count of stale pending trades")
                .register(meterRegistry);
    }

    /**
     * Check for stale pending trades every minute.
     */
    @Scheduled(fixedRateString = "${fxanalyzer.trade.stale-check-interval-ms:60000}")
    public void checkForStaleTrades() {
        log.debug("Checking for stale pending trades (timeout: {} minutes)", executionTimeoutMinutes);

        try {
            // Get count of stale trades
            int staleCount = tradeExecutionService.getStalePendingTrades(executionTimeoutMinutes).size();
            stalePendingCount.set(staleCount);

            if (staleCount > 0) {
                log.warn("Found {} stale pending trades", staleCount);

                // Mark them as failed
                int markedFailed = tradeExecutionService.markStalePendingTradesAsFailed(executionTimeoutMinutes);
                log.info("Marked {} stale trades as failed", markedFailed);
            }

        } catch (Exception e) {
            log.error("Error checking for stale trades: {}", e.getMessage(), e);
        }
    }
}
