package com.vyshali.positionloader.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineCacheBuilder());
        cacheManager.setCacheNames(java.util.List.of(
            AppConfig.CACHE_ACCOUNTS,
            AppConfig.CACHE_PRODUCTS,
            AppConfig.CACHE_FX_RATES,
            AppConfig.CACHE_HOLIDAYS,
            AppConfig.CACHE_BUSINESS_DAYS,
            AppConfig.CACHE_YEAR_HOLIDAYS
        ));
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats();
    }
    
    /**
     * Separate cache builder for short-lived caches.
     */
    @Bean
    public Caffeine<Object, Object> shortLivedCacheBuilder() {
        return Caffeine.newBuilder()
            .initialCapacity(50)
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES);
    }
    
    /**
     * Separate cache builder for long-lived caches.
     */
    @Bean
    public Caffeine<Object, Object> longLivedCacheBuilder() {
        return Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(2000)
            .expireAfterWrite(24, TimeUnit.HOURS);
    }
}
