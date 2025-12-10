package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:59 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka event publishing and DLQ management.
 */
@Slf4j
@Service
public class EventService {

    private final KafkaTemplate<String, Object> kafka;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaProperties kafkaProperties;
    private final AtomicLong dlqDepth = new AtomicLong(0);

    private static final String DLQ_TOPIC = KafkaConfig.TOPIC_INTRADAY + ".DLT";

    public EventService(KafkaTemplate<String, Object> kafka, KafkaAdmin kafkaAdmin, KafkaProperties kafkaProperties) {
        this.kafka = kafka;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaProperties = kafkaProperties;
    }

    // ==================== DLQ MONITORING ====================

    @Scheduled(fixedRate = 60000)
    public void monitorDlq() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicPartition tp = new TopicPartition(DLQ_TOPIC, 0);

            var endOffsets = admin.listOffsets(Map.of(tp, OffsetSpec.latest())).all().get();
            var startOffsets = admin.listOffsets(Map.of(tp, OffsetSpec.earliest())).all().get();

            long count = endOffsets.get(tp).offset() - startOffsets.get(tp).offset();
            dlqDepth.set(count);

            if (count > 0) {
                log.warn("DLQ has {} pending messages", count);
            }
        } catch (Exception e) {
            dlqDepth.set(0);
        }
    }

    public long getDlqDepth() {
        return dlqDepth.get();
    }

    // ==================== DLQ REPLAY ====================

    public int replayDlq(String originalTopic) {
        String dlqTopic = originalTopic + ".DLT";
        log.info("Starting DLQ replay: {} -> {}", dlqTopic, originalTopic);

        String groupId = "dlq-replay-" + System.currentTimeMillis();

        try (Consumer<String, String> consumer = createConsumer(groupId)) {
            consumer.subscribe(Collections.singletonList(dlqTopic));
            int replayed = 0;

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));
                if (records.isEmpty()) break;

                for (var record : records) {
                    try {
                        kafka.send(originalTopic, record.key(), record.value()).get();
                        replayed++;
                    } catch (Exception e) {
                        log.error("Replay failed for key {}: {}", record.key(), e.getMessage());
                        throw new RuntimeException("Replay failed", e);
                    }
                }
                consumer.commitSync();
                log.info("Replayed {} messages", records.count());
            }

            log.info("DLQ replay complete: {} messages", replayed);
            return replayed;
        }
    }

    private Consumer<String, String> createConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
