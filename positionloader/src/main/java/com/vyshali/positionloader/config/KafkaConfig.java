package com.vyshali.positionloader.config;

/*
 * 12/1/25 - 20:47
 * @author Vyshali Prabananth Lal
 */

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for Position Loader.
 * <p>
 * Topics:
 * - MSPM_EOD_TRIGGER: EOD triggers from MSPM (account IDs)
 * - MSPA_INTRADAY: Intraday batches from MSPA (position updates)
 * - POSITION_CHANGE_EVENTS: Published when positions change
 * - CLIENT_REPORTING_SIGNOFF: Published when client completes EOD
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== TOPICS ====================

    public static final String TOPIC_EOD_TRIGGER = "MSPM_EOD_TRIGGER";
    public static final String TOPIC_INTRADAY = "MSPA_INTRADAY";
    public static final String TOPIC_POSITION_CHANGES = "POSITION_CHANGE_EVENTS";
    public static final String TOPIC_SIGNOFF = "CLIENT_REPORTING_SIGNOFF";

    // ==================== CONSUMER GROUPS ====================

    public static final String GROUP_EOD = "positionloader-eod-group";
    public static final String GROUP_INTRADAY = "positionloader-intraday-group";

    // ==================== ERROR HANDLING ====================

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        // Send failed messages to DLQ after 3 retries
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }

    // ==================== CONSUMER FACTORY ====================

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ==================== LISTENER FACTORIES ====================

    /**
     * Factory for EOD triggers (single record processing).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> eodFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Factory for intraday batches (batch processing 1-100 records).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}