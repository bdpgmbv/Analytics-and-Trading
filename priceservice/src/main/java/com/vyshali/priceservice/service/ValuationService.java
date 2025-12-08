package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.dto.ValuationDTO;
import com.vyshali.priceservice.service.strategy.PricingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener; // <--- NEW
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationService {

    private final PositionCacheService positionCache;
    private final PriceCacheService priceCache;
    private final WebSocketConflationService conflationService;
    private final List<PricingStrategy> pricingStrategies;
    private final FxCacheService fxCache; // Unidirectional dependency is fine

    @Value("${app.sharding.shard-id:0}")
    private int shardId;

    @Value("${app.sharding.total-shards:1}")
    private int totalShards;

    /**
     * Listens for FX Ripple Events to trigger re-valuation.
     */
    @EventListener
    public void onFxRipple(FxCacheService.FxRippleEvent event) {
        event.affectedProducts().forEach(this::recalculateAndPush);
    }

    /**
     * Core Calculation Logic.
     */
    public void recalculateAndPush(Integer productId) {
        // 1. Get Accounts holding this product
        Set<Integer> allAccounts = positionCache.getAccountsHoldingProduct(productId);

        // 2. Get Data
        PriceTickDTO priceTick = priceCache.getPrice(productId);
        if (allAccounts == null || allAccounts.isEmpty() || priceTick == null) return;

        // 3. Register Currency
        fxCache.registerProductCurrency(productId, priceTick.currency());

        // 4. Select Strategy
        String assetClass = priceCache.getAssetClass(productId);
        PricingStrategy strategy = pricingStrategies.stream().filter(s -> s.supports(assetClass)).findFirst().orElse((q, p, f) -> p.price().multiply(q).multiply(f));

        // 5. SHARDED PROCESSING LOOP
        allAccounts.stream()
                .filter(accountId -> (accountId % totalShards) == shardId).forEach(accountId -> {
                    try {
                        BigDecimal qty = positionCache.getQuantity(accountId, productId);
                        if (qty.compareTo(BigDecimal.ZERO) == 0) return;

                        BigDecimal fxRate = fxCache.getConversionRate(priceTick.currency(), "USD");
                        BigDecimal marketValue = strategy.calculateMarketValue(qty, priceTick, fxRate);

                        ValuationDTO valuation = new ValuationDTO(accountId, productId, marketValue, priceTick.price(), fxRate, "REAL_TIME");
                        conflationService.queueValuation(valuation);

                    } catch (Exception e) {
                        log.error("Valuation calculation error for Account {}", accountId, e);
                    }
                });
    }
}