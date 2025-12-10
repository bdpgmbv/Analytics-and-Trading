package com.vyshali.positionloader.config;

/*
 * 12/1/25 - 20:47
 * @author Vyshali Prabananth Lal
 *
 * FIXED: Added missing eodFactory bean
 * FIXED: Corrected batch listener configuration
 */

import com.vyshali.positionloader.dto.TradeEventDTO;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for EOD and Intraday processing.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ==================== TOPICS & GROUPS ====================

    public static final String TOPIC_EOD_TRIGGER = "MSPM_EOD_TRIGGER";
    public static final String TOPIC_INTRADAY = "MSPA_INTRADAY";
    public static final String TOPIC_POSITION_CHANGES = "POSITION_CHANGE_EVENTS";
    public static final String TOPIC_SIGNOFF = "CLIENT_REPORTING_SIGNOFF";

    public static final String GROUP_EOD = "positionloader-eod-group";
    public static final String GROUP_INTRADAY = "positionloader-intraday-group";

    // ==================== ERROR HANDLING ====================

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }

    // ==================== CONSUMER FACTORIES ====================

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

    @Bean
    public ConsumerFactory<String, TradeEventDTO> tradeConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ErrorHandlingDeserializer<>(new JsonDeserializer<>(TradeEventDTO.class)));
    }

    // ==================== LISTENER FACTORIES ====================

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> eodFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEventDTO> tradeFactory(ConsumerFactory<String, TradeEventDTO> tradeConsumerFactory, CommonErrorHandler kafkaErrorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, TradeEventDTO>();
        factory.setConsumerFactory(tradeConsumerFactory);
        factory.setBatchListener(false);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}