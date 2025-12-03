package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import com.vyshali.priceservice.dto.ValuationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationService {

    private final PositionCacheService positionCache;
    private final PriceCacheService priceCache;
    private final WebSocketConflationService conflationService;

    @Lazy // Break circular dep with FX
    private final FxCacheService fxCache;

    /**
     * Core Calculation Logic.
     * Called by MarketDataListener (Price Change) AND FxCacheService (Ripple Effect).
     */
    public void recalculateAndPush(Integer productId) {
        // 1. Get Accounts that hold this product
        Set<Integer> affectedAccounts = positionCache.getAccountsHoldingProduct(productId);

        // 2. Get Latest Price (from Redis)
        PriceTickDTO priceTick = priceCache.getPrice(productId);

        if (affectedAccounts == null || affectedAccounts.isEmpty() || priceTick == null) return;

        // 3. Register Currency for future Ripple effects
        fxCache.registerProductCurrency(productId, priceTick.currency());

        // 4. Calculate P&L for every account
        affectedAccounts.forEach(accountId -> {
            try {
                BigDecimal qty = positionCache.getQuantity(accountId, productId);
                if (qty.compareTo(BigDecimal.ZERO) == 0) return;

                // FX Conversion (Asset Ccy -> USD)
                BigDecimal fxRate = fxCache.getConversionRate(priceTick.currency(), "USD");

                BigDecimal marketValue = priceTick.price().multiply(qty).multiply(fxRate);

                ValuationDTO valuation = new ValuationDTO(accountId, productId, marketValue, priceTick.price(), fxRate, "REAL_TIME");

                // 5. Send to Conflator (Do NOT send to Socket directly)
                conflationService.queueValuation(valuation);

            } catch (Exception e) {
                log.error("Valuation calculation error", e);
            }
        });
    }
}