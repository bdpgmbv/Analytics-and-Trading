package com.vyshali.common.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicators for infrastructure dependencies.
 * Auto-configured based on available beans.
 */
public class HealthIndicators {

    private HealthIndicators() {}

    /**
     * Redis health check with connection test.
     */
    @Component("redisHealthIndicator")
    @ConditionalOnBean(RedisConnectionFactory.class)
    public static class RedisHealthIndicator implements HealthIndicator {

        private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);
        private final RedisConnectionFactory connectionFactory;

        public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public Health health() {
            try {
                var connection = connectionFactory.getConnection();
                String pong = connection.ping();
                connection.close();
                
                return Health.up()
                        .withDetail("status", "connected")
                        .withDetail("ping", pong)
                        .build();
            } catch (Exception e) {
                log.warn("Redis health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("status", "disconnected")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Kafka health check via producer metrics.
     */
    @Component("kafkaHealthIndicator")
    @ConditionalOnBean(KafkaTemplate.class)
    public static class KafkaHealthIndicator implements HealthIndicator {

        private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
        private final KafkaTemplate<?, ?> kafkaTemplate;

        public KafkaHealthIndicator(KafkaTemplate<?, ?> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public Health health() {
            try {
                var metrics = kafkaTemplate.getProducerFactory().createProducer().metrics();
                boolean hasMetrics = !metrics.isEmpty();
                
                if (hasMetrics) {
                    return Health.up()
                            .withDetail("status", "connected")
                            .withDetail("metricsAvailable", true)
                            .build();
                } else {
                    return Health.unknown()
                            .withDetail("status", "unknown")
                            .withDetail("metricsAvailable", false)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Kafka health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("status", "disconnected")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Database health check with connection validation.
     */
    @Component("databaseHealthIndicator")
    @ConditionalOnBean(DataSource.class)
    public static class DatabaseHealthIndicator implements HealthIndicator {

        private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
        private final DataSource dataSource;

        public DatabaseHealthIndicator(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Health health() {
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(5);
                String dbProduct = conn.getMetaData().getDatabaseProductName();
                
                if (valid) {
                    return Health.up()
                            .withDetail("status", "connected")
                            .withDetail("database", dbProduct)
                            .build();
                } else {
                    return Health.down()
                            .withDetail("status", "invalid")
                            .build();
                }
            } catch (Exception e) {
                log.warn("Database health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("status", "disconnected")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }
    }

    /**
     * Circuit breaker health aggregator - shows state of all circuit breakers.
     */
    @Component("circuitBreakerHealthIndicator")
    @ConditionalOnBean(CircuitBreakerRegistry.class)
    public static class CircuitBreakerHealthIndicator implements HealthIndicator {

        private final CircuitBreakerRegistry registry;

        public CircuitBreakerHealthIndicator(CircuitBreakerRegistry registry) {
            this.registry = registry;
        }

        @Override
        public Health health() {
            Map<String, Object> details = new HashMap<>();
            boolean allClosed = true;
            boolean anyOpen = false;

            for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
                CircuitBreaker.State state = cb.getState();
                details.put(cb.getName(), Map.of(
                        "state", state.name(),
                        "failureRate", cb.getMetrics().getFailureRate(),
                        "slowCallRate", cb.getMetrics().getSlowCallRate()
                ));

                if (state == CircuitBreaker.State.OPEN) {
                    anyOpen = true;
                    allClosed = false;
                } else if (state == CircuitBreaker.State.HALF_OPEN) {
                    allClosed = false;
                }
            }

            Health.Builder builder;
            if (anyOpen) {
                builder = Health.down().withDetail("status", "DEGRADED");
            } else if (allClosed) {
                builder = Health.up().withDetail("status", "HEALTHY");
            } else {
                builder = Health.status("RECOVERING").withDetail("status", "RECOVERING");
            }

            return builder.withDetails(details).build();
        }
    }
}
