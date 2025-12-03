package com.vyshali.priceservice.listener;

/*
 * 12/02/2025 - 6:48 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.priceservice.dto.PositionChangeDTO;
import com.vyshali.priceservice.service.PositionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionListener {

    private final PositionCacheService positionCache;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "POSITION_CHANGE_EVENTS", groupId = "price-pos-group", containerFactory = "batchFactory")
    public void onPositionChange(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            // Note: You need to define PositionChangeDTO to match the publisher
            PositionChangeDTO dto = objectMapper.readValue(record.value(), PositionChangeDTO.class);

            // Assume the DTO contains the NEW total quantity.
            // Ideally, PositionLoader should emit "Current Quantity", not just delta.
            positionCache.updatePosition(dto.accountId(), dto.productId(), dto.newQuantity());

        } catch (Exception e) {
            log.error("Position Parse Error", e);
        }
        ack.acknowledge();
    }
}
