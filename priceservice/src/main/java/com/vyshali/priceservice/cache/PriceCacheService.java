package com.vyshali.priceservice.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vyshali.fxanalyzer.common.dto.FxRateDto;
import com.vyshali.fxanalyzer.common.dto.PriceDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Two-level cache service for prices and FX rates.
 * 
 * L1: Caffeine (in-memory, ultra-fast, short TTL)
 * L2: Redis (distributed, longer TTL)
 * 
 * Read path: L1 -> L2 -> Database
 * Write path: Database -> L2 -> L1
 */
@Slf4j
@Service
public class PriceCacheService {

    private static final String PRICE_KEY_PREFIX = "price:";
    private static final String FX_RATE_KEY_PREFIX = "fxrate:";
    
    private final Cache<String, PriceDto> l1PriceCache;
    private final Cache<String, FxRateDto> l1FxRateCache;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Metrics
    private final Counter l1HitCounter;
    private final Counter l2HitCounter;
    private final Counter cacheMissCounter;

    public PriceCacheService(RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        
        // Initialize L1 caches
        this.l1PriceCache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
                .build();
        
        this.l1FxRateCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats()
                .build();
        
        // Initialize metrics
        this.l1HitCounter = Counter.builder("price.cache.l1.hits")
                .description("L1 cache hits")
                .register(meterRegistry);
        
        this.l2HitCounter = Counter.builder("price.cache.l2.hits")
                .description("L2 cache hits")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("price.cache.misses")
                .description("Cache misses")
                .register(meterRegistry);
    }

    // ==================== Price Cache Operations ====================

    /**
     * Get price from cache (L1 -> L2)
     */
    public Optional<PriceDto> getPrice(Long productId) {
        String key = PRICE_KEY_PREFIX + productId;
        
        // Try L1 first
        PriceDto l1Result = l1PriceCache.getIfPresent(key);
        if (l1Result != null) {
            l1HitCounter.increment();
            log.debug("L1 cache hit for price: {}", productId);
            return Optional.of(l1Result);
        }
        
        // Try L2 (Redis)
        try {
            Object l2Result = redisTemplate.opsForValue().get(key);
            if (l2Result instanceof PriceDto) {
                l2HitCounter.increment();
                PriceDto price = (PriceDto) l2Result;
                // Populate L1 from L2
                l1PriceCache.put(key, price);
                log.debug("L2 cache hit for price: {}", productId);
                return Optional.of(price);
            }
        } catch (Exception e) {
            log.warn("Redis error getting price {}: {}", productId, e.getMessage());
        }
        
        cacheMissCounter.increment();
        log.debug("Cache miss for price: {}", productId);
        return Optional.empty();
    }

    /**
     * Put price in cache (L1 + L2)
     */
    public void putPrice(Long productId, PriceDto price) {
        String key = PRICE_KEY_PREFIX + productId;
        
        // Update L1
        l1PriceCache.put(key, price);
        
        // Update L2
        try {
            redisTemplate.opsForValue().set(key, price, Duration.ofMinutes(5));
            log.debug("Cached price for product {}", productId);
        } catch (Exception e) {
            log.warn("Redis error caching price {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Evict price from cache
     */
    public void evictPrice(Long productId) {
        String key = PRICE_KEY_PREFIX + productId;
        l1PriceCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis error evicting price {}: {}", productId, e.getMessage());
        }
    }

    // ==================== FX Rate Cache Operations ====================

    /**
     * Get FX rate from cache (L1 -> L2)
     */
    public Optional<FxRateDto> getFxRate(String currencyPair) {
        String key = FX_RATE_KEY_PREFIX + currencyPair;
        
        // Try L1 first
        FxRateDto l1Result = l1FxRateCache.getIfPresent(key);
        if (l1Result != null) {
            l1HitCounter.increment();
            return Optional.of(l1Result);
        }
        
        // Try L2 (Redis)
        try {
            Object l2Result = redisTemplate.opsForValue().get(key);
            if (l2Result instanceof FxRateDto) {
                l2HitCounter.increment();
                FxRateDto rate = (FxRateDto) l2Result;
                l1FxRateCache.put(key, rate);
                return Optional.of(rate);
            }
        } catch (Exception e) {
            log.warn("Redis error getting FX rate {}: {}", currencyPair, e.getMessage());
        }
        
        cacheMissCounter.increment();
        return Optional.empty();
    }

    /**
     * Put FX rate in cache (L1 + L2)
     */
    public void putFxRate(String currencyPair, FxRateDto rate) {
        String key = FX_RATE_KEY_PREFIX + currencyPair;
        
        l1FxRateCache.put(key, rate);
        
        try {
            redisTemplate.opsForValue().set(key, rate, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Redis error caching FX rate {}: {}", currencyPair, e.getMessage());
        }
    }

    /**
     * Evict FX rate from cache
     */
    public void evictFxRate(String currencyPair) {
        String key = FX_RATE_KEY_PREFIX + currencyPair;
        l1FxRateCache.invalidate(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis error evicting FX rate {}: {}", currencyPair, e.getMessage());
        }
    }

    // ==================== Cache Stats ====================

    public CacheStats getStats() {
        return CacheStats.builder()
                .l1PriceSize(l1PriceCache.estimatedSize())
                .l1FxRateSize(l1FxRateCache.estimatedSize())
                .l1PriceHitRate(l1PriceCache.stats().hitRate())
                .l1FxRateHitRate(l1FxRateCache.stats().hitRate())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private long l1PriceSize;
        private long l1FxRateSize;
        private double l1PriceHitRate;
        private double l1FxRateHitRate;
    }
}
