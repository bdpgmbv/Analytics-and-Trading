package com.vyshali.priceservice.service;

/*
 * 12/03/2025 - 12:43 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.ValuationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketConflationService {

    private final SimpMessagingTemplate webSocket;

    // Mailbox: AccountID -> (ProductID -> LatestValuation)
    // We only keep the *latest* calculation for any given product per account.
    private final Map<Integer, Map<Integer, ValuationDTO>> pendingUpdates = new ConcurrentHashMap<>();

    public void queueValuation(ValuationDTO valuation) {
        pendingUpdates.computeIfAbsent(valuation.accountId(), k -> new ConcurrentHashMap<>()).put(valuation.productId(), valuation);
    }

    /**
     * FLUSH JOB: Runs every 250ms.
     * Merges high-frequency updates into a single batch per account.
     */
    @Scheduled(fixedRate = 250)
    public void broadcast() {
        if (pendingUpdates.isEmpty()) return;

        for (Integer accountId : pendingUpdates.keySet()) {
            Map<Integer, ValuationDTO> accountUpdates = pendingUpdates.remove(accountId);

            if (accountUpdates != null && !accountUpdates.isEmpty()) {
                List<ValuationDTO> batch = new ArrayList<>(accountUpdates.values());

                // Push Batch to UI Topic
                webSocket.convertAndSend("/topic/account/" + accountId, batch);
            }
        }
    }
}
