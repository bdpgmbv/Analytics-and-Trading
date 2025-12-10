package com.vyshali.positionloader.config;

/*
 * SIMPLIFIED: Removed 4 unused @Bean methods
 *
 * DELETED:
 * - clientsCacheBuilder() - was never injected anywhere
 * - fundsCacheBuilder() - was never injected anywhere
 * - activeBatchCacheBuilder() - was never injected anywhere
 * - multiTtlCacheManager() - not used because cacheManager() is @Primary
 *
 * KEPT:
 * - cacheManager() with per-cache TTL configuration
 */

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /*
     * Cache TTL Strategy:
     * | Cache Name      | TTL      | Reasoning                          |
     * |-----------------|----------|------------------------------------|
     * | clients, funds  | 1 hour   | Rarely changes                     |
     * | accounts        | 30 min   | May change during onboarding       |
     * | products        | 30 min   | New products added occasionally    |
     * | activeBatch     | 5 min    | Changes during EOD batch swap      |
     * | clientAccounts  | 30 min   | For client completion check        |
     */

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager() {
            @Override
            protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
                return getCaffeineSpec(name).build();
            }
        };

        manager.setCacheNames(List.of("clients", "funds", "accounts", "products", "activeBatch", "clientAccounts"));

        log.info("CacheManager initialized with caches: {}", manager.getCacheNames());
        return manager;
    }

    /**
     * Returns cache configuration based on cache name.
     * Different caches have different TTLs based on how often data changes.
     */
    private Caffeine<Object, Object> getCaffeineSpec(String cacheName) {
        return switch (cacheName) {
            // Long TTL (1 hour) - data rarely changes
            case "clients", "funds" ->
                    Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(1, TimeUnit.HOURS).recordStats();

            // Short TTL (5 minutes) - changes during EOD
            case "activeBatch" ->
                    Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(5, TimeUnit.MINUTES).recordStats();

            // Medium TTL (30 minutes) - default for most caches
            default -> Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(30, TimeUnit.MINUTES).recordStats();
        };
    }
}