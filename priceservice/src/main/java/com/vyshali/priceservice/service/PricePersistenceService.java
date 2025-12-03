package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:47 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.repository.PriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePersistenceService {
    private final PriceCacheService priceCache;
    private final PriceRepository priceRepo;
    private final Set<Integer> dirtyProducts = ConcurrentHashMap.newKeySet();

    public void markDirty(Integer productId) {
        dirtyProducts.add(productId);
    }

    @Scheduled(fixedRate = 1000) // Flush every 1s
    public void flushPrices() {
        if (dirtyProducts.isEmpty()) return;
        List<PriceTickDTO> batch = new ArrayList<>();
        for (Integer id : dirtyProducts) {
            PriceTickDTO latest = priceCache.getPrice(id);
            if (latest != null) batch.add(latest);
        }
        dirtyProducts.clear();
        if (!batch.isEmpty()) priceRepo.batchInsertPrices(batch);
    }
}
