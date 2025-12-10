package com.vyshali.positionloader.config;

/*
 * CLEANED: Removed unused TOPIC_SIGNOFF constant
 *
 * BEFORE:
 *   TOPIC_SIGNOFF = "CLIENT_REPORTING_SIGNOFF" (defined but never consumed)
 *
 * ANALYSIS:
 *   - Position Loader PRODUCES to this topic (when sign-off happens)
 *   - Position Loader does NOT CONSUME from this topic
 *   - Other services (Client Reporting) consume from it
 *
 * DECISION:
 *   - KEEP the constant IF we produce to it
 *   - REMOVE if we neither produce nor consume
 *
 * In this case, we DO produce to it, so we keep it but document its purpose.
 */

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // TOPIC CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    // Topics we CONSUME from:
    public static final String TOPIC_EOD_TRIGGER = "MSPM_EOD_TRIGGER";
    public static final String TOPIC_INTRADAY = "MSPA_INTRADAY";

    // Topics we PRODUCE to:
    public static final String TOPIC_POSITION_CHANGES = "POSITION_CHANGE_EVENTS";

    // Topics we PRODUCE to (but don't consume):
    // Used by Client Reporting service for sign-off events
    public static final String TOPIC_SIGNOFF = "CLIENT_REPORTING_SIGNOFF";  // KEEP - we produce to this

    // ═══════════════════════════════════════════════════════════════════════════
    // TOPIC DOCUMENTATION
    // ═══════════════════════════════════════════════════════════════════════════
    //
    // Topic Flow:
    //
    // MSPM_EOD_TRIGGER      →  [Position Loader]  →  POSITION_CHANGE_EVENTS
    //                                             →  CLIENT_REPORTING_SIGNOFF
    // MSPA_INTRADAY         →  [Position Loader]  →  POSITION_CHANGE_EVENTS
    //
    // Consumers of our topics:
    // - POSITION_CHANGE_EVENTS: PriceService, HedgeService, RiskService
    // - CLIENT_REPORTING_SIGNOFF: ClientReportingService
    //
    // ═══════════════════════════════════════════════════════════════════════════

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Batch factory for all listeners.
     * Both EOD and Intraday use batch processing.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(ConsumerFactory<String, String> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}