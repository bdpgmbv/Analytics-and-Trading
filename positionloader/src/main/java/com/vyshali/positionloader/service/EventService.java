package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - Created: EventService for DLQ replay
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/**
 * Service for event management including DLQ replay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Replay messages from a Dead Letter Queue topic back to the original topic.
     *
     * @param dlqTopic The DLQ topic name (e.g., "MSPM_EOD_TRIGGER.DLT")
     * @return Number of messages replayed
     */
    public int replayDlq(String dlqTopic) {
        log.info("Starting DLQ replay for topic: {}", dlqTopic);

        // Derive original topic from DLQ topic name
        String originalTopic = dlqTopic.replace(".DLT", "");

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "dlq-replay-" + System.currentTimeMillis());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("max.poll.records", "100");

        int count = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(dlqTopic));

            // Poll for messages (with timeout)
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    // Republish to original topic
                    kafkaTemplate.send(originalTopic, record.key(), record.value()).get();
                    count++;
                    log.debug("Replayed message offset={} to topic={}", record.offset(), originalTopic);
                } catch (Exception e) {
                    log.error("Failed to replay message offset={}: {}", record.offset(), e.getMessage());
                }
            }

            // Commit offsets after successful replay
            if (count > 0) {
                consumer.commitSync();
            }

        } catch (Exception e) {
            log.error("DLQ replay failed for topic {}: {}", dlqTopic, e.getMessage());
            throw new RuntimeException("DLQ replay failed: " + e.getMessage(), e);
        }

        log.info("DLQ replay complete: {} messages replayed from {} to {}", count, dlqTopic, originalTopic);

        return count;
    }

    /**
     * Get count of messages in a DLQ topic.
     */
    public long getDlqCount(String dlqTopic) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "dlq-count-" + System.currentTimeMillis());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(dlqTopic));

            // Quick poll to get assignment
            consumer.poll(Duration.ofMillis(100));

            // Get end offsets
            return consumer.endOffsets(consumer.assignment()).values().stream().mapToLong(Long::longValue).sum();

        } catch (Exception e) {
            log.warn("Failed to get DLQ count for {}: {}", dlqTopic, e.getMessage());
            return -1;
        }
    }
}