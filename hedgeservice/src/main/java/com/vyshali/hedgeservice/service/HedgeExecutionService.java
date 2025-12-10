package com.vyshali.hedgeservice.service;

/*
 * 12/10/2025 - FIXED: FixEngine.sendOrder() call to use correct method signature
 *
 * BEFORE (Wrong):
 *   String clOrdId = fixEngine.sendOrder(request);
 *
 * AFTER (Correct):
 *   boolean isBuy = "BUY".equalsIgnoreCase(request.side());
 *   String clOrdId = fixEngine.sendMarketOrder(
 *       request.currencyPair(),
 *       isBuy,
 *       request.quantity(),
 *       null  // callback
 *   );
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import com.vyshali.hedgeservice.fix.FixEngine;
import com.vyshali.hedgeservice.repository.HedgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeExecutionService {

    private final FixEngine fixEngine;
    private final HedgeRepository hedgeRepository;

    @Transactional
    public String executeHedge(HedgeExecutionRequestDTO request) {
        log.info("Initiating Hedge Order: Pair={} Qty={} Side={}", request.currencyPair(), request.quantity(), request.side());

        // 1. PERSIST INTENT (PENDING)
        // If the app crashes after this line but before sendOrder, we have a record.
        String internalId = hedgeRepository.createOrder(request);

        try {
            // 2. SEND TO EXCHANGE
            // FIXED: Use correct method signature
            // FixEngine.sendMarketOrder(currencyPair, isBuy, quantity, callback)
            boolean isBuy = "BUY".equalsIgnoreCase(request.side());

            String clOrdId = fixEngine.sendMarketOrder(request.currencyPair(), isBuy, request.quantity(),
                    // Optional callback for execution reports
                    (clOrdIdCb, execType, fillQty, fillPx, ordStatus) -> {
                        log.info("Execution Report: clOrdId={}, execType={}, status={}", clOrdIdCb, execType, ordStatus);
                        // Update order status based on execution report
                        if ("2".equals(ordStatus)) { // FILLED
                            hedgeRepository.updateOrderStatus(internalId, clOrdIdCb, "FILLED");
                        } else if ("8".equals(ordStatus)) { // REJECTED
                            hedgeRepository.updateOrderStatus(internalId, clOrdIdCb, "REJECTED");
                        }
                    });

            // 3. UPDATE TO SENT
            hedgeRepository.updateOrderStatus(internalId, clOrdId, "SENT");

            log.info("Hedge Order sent: internalId={}, clOrdId={}", internalId, clOrdId);
            return internalId; // Return our ID, not the FIX ID

        } catch (Exception e) {
            log.error("Failed to send FIX order: {}", e.getMessage());
            hedgeRepository.updateOrderStatus(internalId, null, "FAILED");
            throw new RuntimeException("Failed to execute hedge: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a limit order hedge.
     */
    @Transactional
    public String executeHedgeLimit(HedgeExecutionRequestDTO request, java.math.BigDecimal limitPrice) {
        log.info("Initiating Limit Hedge Order: Pair={} Qty={} Side={} Price={}", request.currencyPair(), request.quantity(), request.side(), limitPrice);

        String internalId = hedgeRepository.createOrder(request);

        try {
            boolean isBuy = "BUY".equalsIgnoreCase(request.side());

            String clOrdId = fixEngine.sendLimitOrder(request.currencyPair(), isBuy, request.quantity(), limitPrice, null  // callback optional
            );

            hedgeRepository.updateOrderStatus(internalId, clOrdId, "SENT");
            return internalId;

        } catch (Exception e) {
            log.error("Failed to send limit order: {}", e.getMessage());
            hedgeRepository.updateOrderStatus(internalId, null, "FAILED");
            throw new RuntimeException("Failed to execute limit hedge: " + e.getMessage(), e);
        }
    }

    /**
     * Check if FIX engine is connected.
     */
    public boolean isFixConnected() {
        return fixEngine.isConnected();
    }

    /**
     * Get count of pending orders.
     */
    public int getPendingOrderCount() {
        return fixEngine.getPendingOrderCount();
    }
}