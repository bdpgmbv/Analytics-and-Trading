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

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ============================================================
    // ERROR HANDLING
    // ============================================================

    @Bean
    public DeadLetterPublishingRecoverer recoverer(KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(template);
    }

    @Bean
    public CommonErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    // ============================================================
    // CONSUMER FACTORIES
    // ============================================================

    /**
     * Consumer Factory for simple String messages (EOD Triggers)
     */
    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, TopicConstants.GROUP_EOD);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Consumer Factory for TradeEventDTO messages (Intraday updates)
     */
    @Bean
    public ConsumerFactory<String, TradeEventDTO> tradeConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, TopicConstants.GROUP_INTRADAY);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ErrorHandlingDeserializer<>(new JsonDeserializer<>(TradeEventDTO.class)));
    }

    // ============================================================
    // LISTENER CONTAINER FACTORIES
    // ============================================================

    /**
     * EOD Factory - For processing EOD trigger messages (single record processing)
     * Used by: MarketDataListener.onEodTrigger()
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> eodFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler errorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(false);  // Single record processing
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Intraday Factory - For processing intraday batch messages
     * Used by: MarketDataListener.onIntradayBatch(), IntradayListener
     * <p>
     * FIXED: setBatchListener(true) to match the List<ConsumerRecord> parameter in listeners
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> intradayBatchFactory(ConsumerFactory<String, String> stringConsumerFactory, CommonErrorHandler errorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(stringConsumerFactory);
        factory.setBatchListener(true);  // FIXED: Batch processing for List<ConsumerRecord>
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Intraday Factory for TradeEventDTO - Single record processing
     * Used by: IntradayListener.onTradeEvent()
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEventDTO> intradayFactory(ConsumerFactory<String, TradeEventDTO> tradeConsumerFactory, CommonErrorHandler errorHandler) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, TradeEventDTO>();
        factory.setConsumerFactory(tradeConsumerFactory);
        factory.setBatchListener(false);  // Single record for validated DTOs
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}