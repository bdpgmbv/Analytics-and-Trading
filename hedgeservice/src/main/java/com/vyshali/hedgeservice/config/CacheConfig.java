package com.vyshali.hedgeservice.config;

/*
 * 12/11/2025 - Cache Configuration for Hedge Service
 * @author Vyshali Prabananth Lal
 *
 * Configures Redis-backed caching with TTL for hedge position grids.
 * Cache invalidation is triggered by Kafka events from Position Loader.
 */

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Per-cache TTL configurations
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Hedge positions grid - 10 minute TTL (invalidated by Kafka events)
        cacheConfigs.put("hedgePositions", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // Transaction view - 5 minute TTL (transactions change less frequently)
        cacheConfigs.put("transactionView", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Forward maturity alerts - 15 minute TTL
        cacheConfigs.put("forwardAlerts", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Cash management - 5 minute TTL
        cacheConfigs.put("cashManagement", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }
}
