package com.vyshali.positionloader.service;

/*
 * 12/02/2025 - 11:15 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.TopicConstants;
import com.vyshali.positionloader.dto.ChangeEventDTO;
import com.vyshali.positionloader.dto.SignOffEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishChangeEvent(Integer accountId, Integer clientId, int posCount, String type) {
        // Using Java 21 Record Constructor
        var event = new ChangeEventDTO(type, accountId, clientId, posCount, Instant.now());

        kafkaTemplate.send(TopicConstants.TOPIC_CHANGE_EVENTS, accountId.toString(), event);
        log.info("Event Published: {} for Account {}", type, accountId);
    }

    public void publishReportingSignOff(Integer clientId, LocalDate date) {
        var event = new SignOffEventDTO(clientId, date, "READY_FOR_REPORTING", 0);

        kafkaTemplate.send(TopicConstants.TOPIC_SIGNOFF, clientId.toString(), event);
        log.info("SignOff Published for Client {}", clientId);
    }
}