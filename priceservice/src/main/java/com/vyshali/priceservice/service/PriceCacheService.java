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
     * Uses Redis to share state across multiple instances of PriceService.
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
}