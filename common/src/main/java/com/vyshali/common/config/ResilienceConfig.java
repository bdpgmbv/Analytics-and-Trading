package com.vyshali.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Shared Resilience4j configuration for all microservices.
 * Provides circuit breakers and retry policies for Redis, Kafka, Database, and external APIs.
 */
@Configuration
public class ResilienceConfig {

    // Circuit breaker names - use these constants across services
    public static final String REDIS = "redis";
    public static final String KAFKA = "kafka";
    public static final String DATABASE = "database";
    public static final String EXTERNAL_API = "external-api";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Redis - fast fail, quick recovery
        CircuitBreakerConfig redisConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofMillis(500))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        // Kafka - slightly more tolerant
        CircuitBreakerConfig kafkaConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();

        // Database - conservative settings
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)
                .slowCallRateThreshold(90)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .build();

        // External API - typical HTTP service
        CircuitBreakerConfig apiConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return CircuitBreakerRegistry.of(Map.of(
                REDIS, redisConfig,
                KAFKA, kafkaConfig,
                DATABASE, dbConfig,
                EXTERNAL_API, apiConfig
        ));
    }

    @Bean
    public RetryRegistry retryRegistry() {
        // Standard retry for transient failures
        RetryConfig standardRetry = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .retryExceptions(IOException.class, SocketTimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        // Database retry - fewer attempts, shorter wait
        RetryConfig dbRetry = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .build();

        // Kafka retry - longer backoff
        RetryConfig kafkaRetry = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .build();

        return RetryRegistry.of(Map.of(
                "standard", standardRetry,
                DATABASE, dbRetry,
                KAFKA, kafkaRetry
        ));
    }

    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(REDIS);
    }

    @Bean
    public CircuitBreaker kafkaCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(KAFKA);
    }

    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(DATABASE);
    }

    @Bean
    public Retry standardRetry(RetryRegistry registry) {
        return registry.retry("standard");
    }
}
