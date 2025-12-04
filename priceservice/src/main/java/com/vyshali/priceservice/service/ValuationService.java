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
import org.springframework.context.annotation.Lazy;
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
    private final List<PricingStrategy> pricingStrategies; // Injected List of Strategies

    @Lazy
    private final FxCacheService fxCache;

    public void recalculateAndPush(Integer productId) {
        Set<Integer> affectedAccounts = positionCache.getAccountsHoldingProduct(productId);
        PriceTickDTO priceTick = priceCache.getPrice(productId);
        String assetClass = priceCache.getAssetClass(productId); // Assuming cached metadata

        if (affectedAccounts == null || affectedAccounts.isEmpty() || priceTick == null) return;

        fxCache.registerProductCurrency(productId, priceTick.currency());

        // Select Strategy (Tier 1 Pattern)
        PricingStrategy strategy = pricingStrategies.stream().filter(s -> s.supports(assetClass)).findFirst().orElse((qty, tick, fx) -> tick.price().multiply(qty).multiply(fx)); // Default fallback

        affectedAccounts.forEach(accountId -> {
            try {
                BigDecimal qty = positionCache.getQuantity(accountId, productId);
                if (qty.compareTo(BigDecimal.ZERO) == 0) return;

                BigDecimal fxRate = fxCache.getConversionRate(priceTick.currency(), "USD");

                // Polymorphic Calculation
                BigDecimal marketValue = strategy.calculateMarketValue(qty, priceTick, fxRate);

                ValuationDTO valuation = new ValuationDTO(accountId, productId, marketValue, priceTick.price(), fxRate, "REAL_TIME");
                conflationService.queueValuation(valuation);

            } catch (Exception e) {
                log.error("Valuation calculation error", e);
            }
        });
    }
}