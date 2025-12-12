package com.vyshali.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caching configuration for FX Analyzer.
 * Uses Caffeine for local caching (L1) with Redis as distributed cache (L2).
 * Addresses Issue #8: Poor position caching
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Cache names
    public static final String CACHE_PRICES = "prices";
    public static final String CACHE_FX_RATES = "fxRates";
    public static final String CACHE_POSITIONS = "positions";
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_ACCOUNTS = "accounts";
    public static final String CACHE_CLIENTS = "clients";

    /**
     * Primary cache manager using Caffeine (local in-memory cache)
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineCacheBuilder());
        cacheManager.setCacheNames(java.util.List.of(
                CACHE_PRICES,
                CACHE_FX_RATES,
                CACHE_POSITIONS,
                CACHE_PRODUCTS,
                CACHE_ACCOUNTS,
                CACHE_CLIENTS
        ));
        return cacheManager;
    }

    /**
     * Caffeine cache configuration
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats();
    }

    /**
     * Cache configuration for prices (shorter TTL for real-time data)
     */
    @Bean
    public Caffeine<Object, Object> priceCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(1000)
                .maximumSize(100_000)
                .expireAfterWrite(30, TimeUnit.SECONDS)  // Prices expire quickly
                .recordStats();
    }

    /**
     * Cache configuration for FX rates
     */
    @Bean
    public Caffeine<Object, Object> fxRateCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.MINUTES)  // FX rates refresh frequently
                .recordStats();
    }

    /**
     * Cache configuration for static reference data (longer TTL)
     */
    @Bean
    public Caffeine<Object, Object> referenceDataCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(500)
                .maximumSize(50_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)  // Reference data is more stable
                .recordStats();
    }
}
