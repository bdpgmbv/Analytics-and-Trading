package com.vyshali.positionloader.service;

/*
 * CLEANED: EventService - Removed unused getDlqCount() method
 *
 * ANALYSIS:
 *   getDlqCount() was defined but never called anywhere in the codebase.
 *
 * OPTIONS:
 *   A) Remove the method (chosen)
 *   B) Actually use it in a health check or dashboard
 *   C) Delete entire EventService if no methods are used
 *
 * RECOMMENDATION:
 *   If EventService only had getDlqCount(), consider deleting the entire class.
 *   Kafka consumer lag can be monitored via:
 *   - Prometheus metrics (kafka_consumer_lag)
 *   - Kafka Manager / AKHQ
 *   - Confluent Control Center
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Publish event to Kafka topic.
     */
    public void publishEvent(String topic, String key, String payload) {
        log.debug("Publishing to {}: key={}", topic, key);
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to {}: {}", topic, ex.getMessage());
            } else {
                log.debug("Published to {} partition {} offset {}", topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish event with account ID as key.
     */
    public void publishPositionChange(Integer accountId, String payload) {
        publishEvent("POSITION_CHANGE_EVENTS", accountId.toString(), payload);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOVED: getDlqCount() - never called
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // The method below was never used. DLQ monitoring should be done via:
    // - Prometheus: kafka_consumer_records_lag_max
    // - Actuator endpoint: /actuator/kafka
    // - External tools: AKHQ, Kafka Manager, Confluent Control Center
    //
    // DELETED CODE:
    // public long getDlqCount() {
    //     // This was never called from anywhere
    //     return kafkaTemplate.execute(operations -> {
    //         // ... complex logic to count DLQ messages
    //         return 0L;
    //     });
    // }
    //
    // ═══════════════════════════════════════════════════════════════════════════
}