package com.vyshali.priceservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vyshali.priceservice.dto.PriceTickDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * IMPROVED: Price cache with Redis primary + Caffeine fallback.
 * Circuit breaker prevents Redis failures from cascading.
 */
@Slf4j
@Service
public class PriceCacheService {

    private final RedisTemplate<String, PriceTickDTO> redisTemplate;
    private final CircuitBreaker circuitBreaker;
    
    // Local fallback cache when Redis is down
    private final Cache<Integer, PriceTickDTO> localCache;
    
    private static final String KEY_PREFIX = "PRICE:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    public PriceCacheService(RedisTemplate<String, PriceTickDTO> redisTemplate,
                             CircuitBreakerRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = registry.circuitBreaker("redis");
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    public void updatePrice(PriceTickDTO tick) {
        if (tick == null || tick.productId() == null) {
            log.warn("Attempted to cache null price tick");
            return;
        }
        
        // Always update local cache
        localCache.put(tick.productId(), tick);
        
        // Try Redis with circuit breaker
        try {
            circuitBreaker.executeRunnable(() -> 
                redisTemplate.opsForValue().set(KEY_PREFIX + tick.productId(), tick, REDIS_TTL)
            );
        } catch (Exception e) {
            log.warn("Redis write failed, using local cache only: {}", e.getMessage());
        }
    }

    public PriceTickDTO getPrice(Integer productId) {
        if (productId == null) return null;
        
        // Try Redis first with circuit breaker
        Supplier<PriceTickDTO> redisLookup = () -> 
            redisTemplate.opsForValue().get(KEY_PREFIX + productId);
        
        try {
            PriceTickDTO result = circuitBreaker.executeSupplier(redisLookup);
            if (result != null) {
                localCache.put(productId, result); // Refresh local
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis read failed, falling back to local: {}", e.getMessage());
        }
        
        // Fallback to local cache
        return localCache.getIfPresent(productId);
    }

    public String getAssetClass(Integer productId) {
        PriceTickDTO tick = getPrice(productId);
        return (tick != null && tick.assetClass() != null) ? tick.assetClass() : "EQUITY";
    }
    
    public boolean isRedisHealthy() {
        return circuitBreaker.getState() == CircuitBreaker.State.CLOSED;
    }
}
