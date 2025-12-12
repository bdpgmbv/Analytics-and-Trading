package com.vyshali.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Shared Caffeine cache configuration.
 * Services can extend this with additional cache names.
 */
@Configuration
public class CacheConfig {

    // Common cache names used across services
    public static final String ACCOUNTS = "accounts";
    public static final String PRODUCTS = "products";
    public static final String FX_RATES = "fxRates";
    public static final String HOLIDAYS = "holidays";
    public static final String BUSINESS_DAYS = "businessDays";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(defaultCacheBuilder());
        manager.setCacheNames(List.of(ACCOUNTS, PRODUCTS, FX_RATES, HOLIDAYS, BUSINESS_DAYS));
        return manager;
    }

    @Bean
    public Caffeine<Object, Object> defaultCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats();
    }

    /**
     * Short-lived cache for frequently changing data.
     */
    @Bean
    public Caffeine<Object, Object> shortLivedCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats();
    }

    /**
     * Long-lived cache for reference data.
     */
    @Bean
    public Caffeine<Object, Object> longLivedCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofHours(24))
                .recordStats();
    }
}
