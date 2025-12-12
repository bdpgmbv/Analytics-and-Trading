package com.vyshali.priceservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Two-level cache configuration:
 * L1: Caffeine (in-memory, fast, short TTL)
 * L2: Redis (distributed, longer TTL)
 * 
 * Price cache strategy:
 * - L1 Caffeine: 30 seconds (for ultra-fast lookups)
 * - L2 Redis: 5 minutes (for distributed consistency)
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_PRICES = "prices";
    public static final String CACHE_FX_RATES = "fxRates";
    public static final String CACHE_PRODUCTS = "products";

    @Value("${fxanalyzer.cache.prices.l1-ttl-seconds:30}")
    private int priceL1TtlSeconds;

    @Value("${fxanalyzer.cache.prices.l2-ttl-seconds:300}")
    private int priceL2TtlSeconds;

    @Value("${fxanalyzer.cache.fx-rates.l1-ttl-seconds:60}")
    private int fxRateL1TtlSeconds;

    @Value("${fxanalyzer.cache.fx-rates.l2-ttl-seconds:300}")
    private int fxRateL2TtlSeconds;

    // ==================== L1 Caffeine Cache ====================

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(java.util.List.of(CACHE_PRICES, CACHE_FX_RATES, CACHE_PRODUCTS));
        cacheManager.setCaffeine(caffeineCacheBuilder());
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(1000)
                .maximumSize(50_000)
                .expireAfterWrite(priceL1TtlSeconds, TimeUnit.SECONDS)
                .recordStats();
    }

    // ==================== L2 Redis Cache ====================

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(priceL2TtlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(CACHE_PRICES, defaultConfig.entryTtl(Duration.ofSeconds(priceL2TtlSeconds)));
        cacheConfigs.put(CACHE_FX_RATES, defaultConfig.entryTtl(Duration.ofSeconds(fxRateL2TtlSeconds)));
        cacheConfigs.put(CACHE_PRODUCTS, defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    // ==================== Redis Template ====================

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
