package com.vyshali.hedgeservice.service;

import com.vyshali.common.logging.LogContext;
import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import com.vyshali.hedgeservice.fix.FixEngine;
import com.vyshali.hedgeservice.repository.HedgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * IMPROVED: Hedge execution with connection validation and retry logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeExecutionService {

    private final FixEngine fixEngine;
    private final HedgeRepository hedgeRepository;

    @Value("${fix.connection.retry-count:3}")
    private int maxRetries;

    @Value("${fix.connection.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Transactional
    public String executeHedge(HedgeExecutionRequestDTO request) {
        return LogContext.with("accountId", request.accountId())
                .and("currencyPair", request.currencyPair())
                .call(() -> doExecuteHedge(request));
    }

    private String doExecuteHedge(HedgeExecutionRequestDTO request) throws Exception {
        log.info("Initiating hedge: Pair={}, Qty={}, Side={}", 
                request.currencyPair(), request.quantity(), request.side());

        // 1. VALIDATE CONNECTION FIRST
        validateFixConnection();

        // 2. PERSIST INTENT
        String internalId = hedgeRepository.createOrder(request);

        try {
            // 3. SEND TO EXCHANGE WITH RETRY
            String clOrdId = sendOrderWithRetry(request, internalId);

            // 4. UPDATE TO SENT
            hedgeRepository.updateOrderStatus(internalId, clOrdId, "SENT");
            log.info("Hedge order sent: internalId={}, clOrdId={}", internalId, clOrdId);
            
            return internalId;

        } catch (Exception e) {
            log.error("Failed to send FIX order: {}", e.getMessage());
            hedgeRepository.updateOrderStatus(internalId, null, "FAILED");
            throw new HedgeExecutionException("Failed to execute hedge: " + e.getMessage(), e);
        }
    }

    /**
     * Validates FIX connection is available before attempting order.
     */
    private void validateFixConnection() {
        if (!fixEngine.isConnected()) {
            log.error("FIX session not connected - cannot execute hedge");
            throw new FixConnectionException("FIX session is not connected");
        }
        log.debug("FIX connection validated");
    }

    /**
     * Send order with retry logic on transient failures.
     */
    @Retryable(
        value = {FixTransientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private String sendOrderWithRetry(HedgeExecutionRequestDTO request, String internalId) {
        boolean isBuy = "BUY".equalsIgnoreCase(request.side());
        
        return fixEngine.sendMarketOrder(
            request.currencyPair(),
            isBuy,
            request.quantity(),
            (clOrdId, execType, fillQty, fillPx, ordStatus) -> 
                handleExecutionReport(internalId, clOrdId, execType, ordStatus)
        );
    }

    private void handleExecutionReport(String internalId, String clOrdId, 
                                       String execType, String ordStatus) {
        log.info("Execution report: clOrdId={}, execType={}, status={}", 
                clOrdId, execType, ordStatus);
        
        String status = switch (ordStatus) {
            case "2" -> "FILLED";
            case "8" -> "REJECTED";
            case "4" -> "CANCELLED";
            case "1" -> "PARTIALLY_FILLED";
            default -> "ACKNOWLEDGED";
        };
        
        hedgeRepository.updateOrderStatus(internalId, clOrdId, status);
    }

    /**
     * Execute limit order hedge.
     */
    @Transactional
    public String executeHedgeLimit(HedgeExecutionRequestDTO request, BigDecimal limitPrice) {
        validateFixConnection();
        
        String internalId = hedgeRepository.createOrder(request);
        
        try {
            boolean isBuy = "BUY".equalsIgnoreCase(request.side());
            String clOrdId = fixEngine.sendLimitOrder(
                request.currencyPair(), isBuy, request.quantity(), limitPrice, null
            );
            hedgeRepository.updateOrderStatus(internalId, clOrdId, "SENT");
            return internalId;
        } catch (Exception e) {
            hedgeRepository.updateOrderStatus(internalId, null, "FAILED");
            throw new HedgeExecutionException("Limit order failed: " + e.getMessage(), e);
        }
    }

    public boolean isFixConnected() {
        return fixEngine.isConnected();
    }

    public int getPendingOrderCount() {
        return fixEngine.getPendingOrderCount();
    }

    // Custom exceptions for better error handling
    public static class HedgeExecutionException extends RuntimeException {
        public HedgeExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FixConnectionException extends RuntimeException {
        public FixConnectionException(String message) {
            super(message);
        }
    }

    public static class FixTransientException extends RuntimeException {
        public FixTransientException(String message) {
            super(message);
        }
    }
}
