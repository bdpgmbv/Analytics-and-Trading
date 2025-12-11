package com.vyshali.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom health indicators for infrastructure dependencies.
 * Add @ConditionalOnBean to enable only when dependencies exist.
 */
public class CustomHealthIndicators {

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class RedisHealthIndicator implements HealthIndicator {
        
        private final RedisConnectionFactory connectionFactory;

        @Override
        public Health health() {
            try {
                connectionFactory.getConnection().ping();
                return Health.up()
                        .withDetail("status", "connected")
                        .build();
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class KafkaHealthIndicator implements HealthIndicator {
        
        private final KafkaTemplate<?, ?> kafkaTemplate;

        @Override
        public Health health() {
            try {
                // Check if producer is functional
                kafkaTemplate.getProducerFactory().createProducer().metrics();
                return Health.up()
                        .withDetail("status", "connected")
                        .build();
            } catch (Exception e) {
                log.warn("Kafka health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }
}
