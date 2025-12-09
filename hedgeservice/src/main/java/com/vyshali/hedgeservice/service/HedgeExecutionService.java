package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 1:47 PM
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
    private final HedgeRepository hedgeRepository; // <--- NEW

    @Transactional
    public String executeHedge(HedgeExecutionRequestDTO request) {
        log.info("Initiating Hedge Order: Pair={} Qty={}", request.currencyPair(), request.quantity());

        // 1. PERSIST INTENT (PENDING)
        // If the app crashes after this line but before sendOrder, we have a record.
        String internalId = hedgeRepository.createOrder(request);

        try {
            // 2. SEND TO EXCHANGE
            String clOrdId = fixEngine.sendOrder(request);

            // 3. UPDATE TO SENT
            hedgeRepository.updateOrderStatus(internalId, clOrdId, "SENT");

            return internalId; // Return our ID, not the FIX ID
        } catch (Exception e) {
            log.error("Failed to send FIX order", e);
            hedgeRepository.updateOrderStatus(internalId, null, "FAILED");
            throw e;
        }
    }
}