package com.vyshali.positionloader.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Single consolidated configuration class.
 * Phase 1 Enhancement #5: Added LoaderConfig for externalized configuration.
 */
@Configuration
@EnableCaching
@org.springframework.scheduling.annotation.EnableScheduling
public class AppConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // KAFKA TOPIC CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════
    public static final String TOPIC_EOD_TRIGGER = "MSPM_EOD_TRIGGER";
    public static final String TOPIC_INTRADAY = "MSPA_INTRADAY";
    public static final String TOPIC_POSITION_CHANGES = "POSITION_CHANGE_EVENTS";

    // ═══════════════════════════════════════════════════════════════════════════
    // EXTERNALIZED CONFIGURATION (Phase 1 - Enhancement #5)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * All tunable loader parameters in one place.
     * Override via application.yml or environment variables.
     *
     * Examples:
     *   LOADER_PARALLEL_THREADS=16     → More parallel EOD processing
     *   LOADER_BATCH_SIZE=1000         → Larger DB batch inserts
     *   LOADER_MAX_UPLOAD_SIZE=20000   → Allow bigger uploads
     */
    @ConfigurationProperties(prefix = "loader")
    public record LoaderConfig(
            int parallelThreads,
            int batchSize,
            int maxUploadSize,
            int dlqMaxRetries,
            long dlqRetryIntervalMs,
            ValidationConfig validation
    ) {
        public LoaderConfig {
            // Sensible defaults if not specified
            if (parallelThreads <= 0) parallelThreads = 8;
            if (batchSize <= 0) batchSize = 500;
            if (maxUploadSize <= 0) maxUploadSize = 10000;
            if (dlqMaxRetries <= 0) dlqMaxRetries = 3;
            if (dlqRetryIntervalMs <= 0) dlqRetryIntervalMs = 300000;
            if (validation == null) validation = new ValidationConfig(true, true, 1000000);
        }

        public record ValidationConfig(
                boolean enabled,
                boolean rejectZeroQuantity,
                long maxPriceThreshold
        ) {
            public ValidationConfig {
                if (maxPriceThreshold <= 0) maxPriceThreshold = 1000000;
            }
        }
    }

    @Bean
    @ConfigurationProperties(prefix = "loader")
    public LoaderConfig loaderConfig() {
        return new LoaderConfig(8, 500, 10000, 3, 300000,
                new LoaderConfig.ValidationConfig(true, true, 1000000));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REST CLIENT (for MSPM)
    // ═══════════════════════════════════════════════════════════════════════════
    @Value("${mspm.base-url:http://localhost:8080}")
    private String mspmBaseUrl;

    @Value("${mspm.timeout:30}")
    private int timeoutSeconds;

    @Bean
    public RestClient mspmClient() {
        return RestClient.builder().baseUrl(mspmBaseUrl).defaultHeader("Accept", "application/json").build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE (Simple Caffeine)
    // ═══════════════════════════════════════════════════════════════════════════
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(30, TimeUnit.MINUTES).recordStats());
        return manager;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KAFKA CONSUMER
    // ═══════════════════════════════════════════════════════════════════════════
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(ConsumerFactory<String, String> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY
    // ═══════════════════════════════════════════════════════════════════════════
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
        })).build();
    }

    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(AbstractHttpConfigurer::disable).build();
    }
}