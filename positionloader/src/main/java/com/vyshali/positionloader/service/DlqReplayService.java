package com.vyshali.positionloader.service;

/*
 * 12/02/2025 - 1:43 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqReplayService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties; // Auto-configured by Spring Boot

    /**
     * Reads messages from {topic}.DLT and republishes them to {topic}.
     * This allows processing to be retried after a bug fix or outage.
     */
    public int replayDlqMessages(String originalTopic) {
        String dlqTopic = originalTopic + ".DLT";
        log.info("Starting DLQ Replay: Reading from {} -> Publishing to {}", dlqTopic, originalTopic);

        // 1. Create a transient consumer with a random group ID
        // This ensures we see ALL messages currently in the DLT, not just new ones.
        String randomGroupId = "dlq-replay-" + System.currentTimeMillis();
        Consumer<String, String> consumer = createReplayConsumer(randomGroupId);

        consumer.subscribe(Collections.singletonList(dlqTopic));

        int replayedCount = 0;

        try {
            // 2. Poll Loop
            while (true) {
                // Poll with a short timeout
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));

                if (records.isEmpty()) {
                    log.info("DLQ is empty or fully read. Stopping replay.");
                    break;
                }

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        // 3. Republish to Original Topic
                        // We preserve the Key to ensure partition ordering is maintained
                        kafkaTemplate.send(originalTopic, record.key(), record.value()).get(); // .get() forces sync send for safety
                        replayedCount++;
                    } catch (Exception e) {
                        log.error("Failed to republish message key: {}", record.key(), e);
                        // Abort replay if we can't send, so we don't commit offsets for failed sends
                        throw new RuntimeException("Replay Send Failed", e);
                    }
                }

                // 4. Commit Offsets (Mark as read from DLT)
                consumer.commitSync();
                log.info("Replayed batch of {} messages...", records.count());
            }
        } catch (Exception e) {
            log.error("Fatal error during DLQ replay", e);
            throw new RuntimeException("Replay Process Failed", e);
        } finally {
            consumer.close();
        }

        log.info("DLQ Replay Finished. Total messages restored: {}", replayedCount);
        return replayedCount;
    }

    /**
     * Helper to create a manual Kafka Consumer using application.yml properties
     */
    private Consumer<String, String> createReplayConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));

        // Overrides for Replay specific behavior
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Start from beginning of DLT
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");   // We commit manually
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");       // Process in small chunks

        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}