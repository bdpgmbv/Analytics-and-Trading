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

    public void updatePrice(PriceTickDTO tick) {
        // PERFECTION: Set TTL to 24 hours.
        // If a stock is delisted or the system restarts, we don't want stale data forever.
        redisTemplate.opsForValue().set(KEY_PREFIX + tick.productId(), tick, Duration.ofHours(24));
    }

    public PriceTickDTO getPrice(Integer productId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + productId);
    }

    public String getAssetClass(Integer productId) {
        PriceTickDTO tick = getPrice(productId);
        return (tick != null && tick.assetClass() != null) ? tick.assetClass() : "EQUITY";
    }
}