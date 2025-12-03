package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:45 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PriceCacheService {
    private final Map<Integer, PriceTickDTO> realTimeCache = new ConcurrentHashMap<>();

    public void updatePrice(PriceTickDTO tick) {
        realTimeCache.put(tick.productId(), tick);
    }

    public PriceTickDTO getPrice(Integer productId) {
        return realTimeCache.get(productId);
    }
}
