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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import java.time.Duration;
import java.util.HashMap;

@Slf4j
@Service
public class DlqReplayService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaProperties kafkaProperties; // Auto-configured by Spring Boot
    private final AtomicLong dlqDepth = new AtomicLong(0);

    // Monitor this topic for lag
    private static final String DLQ_TOPIC = "MSPA_INTRADAY.DLT";

    public DlqReplayService(KafkaTemplate<String, Object> kafkaTemplate, KafkaAdmin kafkaAdmin, MeterRegistry registry, KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaProperties = kafkaProperties;
        // Register the gauge with Prometheus
        Gauge.builder("kafka.dlq.depth", dlqDepth, AtomicLong::get).description("Number of messages in the Dead Letter Queue").register(registry);
    }

    /**
     * Check DLQ size every 60 seconds
     */
    @Scheduled(fixedRate = 60000)
    public void monitorDlqDepth() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicPartition tp = new TopicPartition(DLQ_TOPIC, 0);

            // Get End Offset
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = admin.listOffsets(Map.of(tp, OffsetSpec.latest())).all().get();

            // Get Start Offset
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> startOffsets = admin.listOffsets(Map.of(tp, OffsetSpec.earliest())).all().get();

            long end = endOffsets.get(tp).offset();
            long start = startOffsets.get(tp).offset();
            long count = end - start;

            dlqDepth.set(count);
            if (count > 0) log.warn("ALERT: DLQ has {} pending messages!", count);

        } catch (Exception e) {
            // Topic might not exist yet, ignore
            dlqDepth.set(0);
        }
    }

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