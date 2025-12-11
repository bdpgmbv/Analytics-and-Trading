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
 * Provides caching for reference data to reduce database load.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final int DEFAULT_EXPIRE_MINUTES = 60;
    
    /**
     * Creates the cache manager with default settings.
     * 
     * Caches configured:
     * - accounts: Account reference data
     * - products: Product/security master data  
     * - fxRates: FX rate lookups
     * - holidays: Holiday calendar
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(DEFAULT_CACHE_SIZE)
            .expireAfterWrite(DEFAULT_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .recordStats());
        
        // Register cache names
        cacheManager.setCacheNames(java.util.List.of(
            AppConfig.CACHE_ACCOUNTS,
            AppConfig.CACHE_PRODUCTS,
            AppConfig.CACHE_FX_RATES,
            AppConfig.CACHE_HOLIDAYS
        ));
        
        return cacheManager;
    }
    
    /**
     * Creates a short-lived cache for duplicate detection hashes.
     * Expires after 5 minutes since duplicates are only relevant within a short window.
     */
    @Bean
    public com.github.benmanes.caffeine.cache.Cache<String, String> snapshotHashCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();
    }
}
