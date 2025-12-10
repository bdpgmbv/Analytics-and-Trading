package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - 2:38 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaDlqConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * DLQ Producer - separate from main producer to ensure DLQ always works.
     */
    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // CRITICAL: Enable idempotence for exactly-once DLQ writes
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Dead Letter Publishing Recoverer.
     * Failed messages go to: {original-topic}.DLT
     */
    @Bean
    public DeadLetterPublishingRecoverer dlqRecoverer(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        return new DeadLetterPublishingRecoverer((KafkaOperations<Object, Object>) (KafkaOperations<?, ?>) dlqKafkaTemplate, (record, ex) -> {
            // Log failure before sending to DLQ
            log.error("Sending to DLQ: topic={}, key={}, error={}", record.topic(), record.key(), ex.getMessage());

            // DLQ topic naming: original-topic.DLT
            return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition());
        });
    }

    /**
     * Error Handler with Retry + DLQ.
     * <p>
     * Policy:
     * - Retry 3 times with 1 second interval
     * - After 3 failures, send to DLQ
     * - Never block the consumer
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer dlqRecoverer) {
        // 3 retries, 1 second apart
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlqRecoverer, backOff);

        // Log all retries
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry attempt {} for topic={}, key={}, error={}", deliveryAttempt, record.topic(), record.key(), ex.getMessage());
        });

        // Don't retry these exceptions (they won't succeed on retry)
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class,  // Bad data
                NullPointerException.class,      // Code bug
                ClassCastException.class         // Serialization issue
        );

        return errorHandler;
    }
}