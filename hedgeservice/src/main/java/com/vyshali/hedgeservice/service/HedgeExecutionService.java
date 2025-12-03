package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 1:47 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeExecutionService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Topic that the downstream "FX Matrix" listens to
    private static final String FX_MATRIX_TOPIC = "FX_MATRIX_ORDERS";

    public String executeHedge(HedgeExecutionRequestDTO request) {
        String clOrdId = "HEDGE-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Sending Hedge Order to FX Matrix: ID={} Pair={} Qty={}", clOrdId, request.currencyPair(), request.quantity());

        // In a real system, you might convert this DTO to a FIX message string here
        kafkaTemplate.send(FX_MATRIX_TOPIC, clOrdId, request);

        return clOrdId;
    }
}
