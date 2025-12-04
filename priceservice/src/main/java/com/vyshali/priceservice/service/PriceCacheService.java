package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:45 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PriceCacheService {

    private final RedisTemplate<String, PriceTickDTO> redisTemplate;
    private static final String KEY_PREFIX = "PRICE:";

    /**
     * Updates Redis with the latest tick.
     */
    public void updatePrice(PriceTickDTO tick) {
        redisTemplate.opsForValue().set(KEY_PREFIX + tick.productId(), tick, Duration.ofHours(24));
    }

    /**
     * Fetches the latest effective price from Redis.
     */
    public PriceTickDTO getPrice(Integer productId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + productId);
    }

    /**
     * NEW METHOD: Retrieves asset class from the cached tick.
     * Used by ValuationService to select the correct pricing strategy.
     */
    public String getAssetClass(Integer productId) {
        PriceTickDTO tick = getPrice(productId);
        // Default to EQUITY if missing or not yet cached, to prevent NPE in strategies
        return (tick != null && tick.assetClass() != null) ? tick.assetClass() : "EQUITY";
    }
}