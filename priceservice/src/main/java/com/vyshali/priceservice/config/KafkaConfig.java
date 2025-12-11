package com.vyshali.priceservice.config;

/*
 * 12/11/2025 - Kafka Configuration for Price Service
 * @author Vyshali Prabananth Lal
 *
 * Configures Kafka consumers for high-throughput market data processing.
 * Includes batch listener factory for efficient tick processing.
 */

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:priceservice-group}")
    private String groupId;

    // ============================================================
    // Consumer Factory
    // ============================================================

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // High throughput settings for market data
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024 * 100); // 100KB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // 100ms max wait

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ============================================================
    // Standard Listener Factory (single record processing)
    // ============================================================

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // 3 concurrent consumers
        return factory;
    }

    // ============================================================
    // BATCH Listener Factory (for high-throughput market data)
    // Used by MarketDataListener and PositionListener
    // ============================================================

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        // CRITICAL: Enable batch processing
        factory.setBatchListener(true);

        // Manual acknowledgment after processing entire batch
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Concurrency based on topic partitions (adjust as needed)
        factory.setConcurrency(4);

        // Idle event interval for monitoring
        factory.getContainerProperties().setIdleEventInterval(60000L);

        return factory;
    }

    // ============================================================
    // Producer Factory (for publishing valuations)
    // ============================================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Batching for efficiency
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ============================================================
    // Error Handler with Dead Letter Queue
    // ============================================================

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    log.error("Market data processing failed - Topic: {}, Key: {}, Error: {}",
                            record.topic(), record.key(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLQ", record.partition());
                }
        );

        // 2 retries with 500ms backoff (fast retry for market data)
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(500L, 2L)
        );

        // Skip retries for data format errors
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonParseException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}
