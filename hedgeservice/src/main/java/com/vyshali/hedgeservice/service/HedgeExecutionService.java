package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 1:47 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import com.vyshali.hedgeservice.fix.FixEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeExecutionService {

    private final FixEngine fixEngine; // <--- Swapped KafkaTemplate for FixEngine

    public String executeHedge(HedgeExecutionRequestDTO request) {
        log.info("Routing Hedge Order via FIX Engine: Pair={} Qty={}", request.currencyPair(), request.quantity());

        // Delegate to the FIX Engine
        return fixEngine.sendOrder(request);
    }
}