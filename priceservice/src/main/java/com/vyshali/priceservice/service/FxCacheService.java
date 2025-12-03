package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FxCacheService {

    // Lazy injection to prevent circular dependency (FX -> Valuation -> FX)
    @Lazy
    private final ValuationService valuationService;

    // Local Hot Cache for FX (Small dataset, safe to keep in heap)
    private final Map<String, FxRateDTO> fxRates = new ConcurrentHashMap<>();

    // REVERSE INDEX: Currency -> List of Products (The "Ripple" Map)
    private final Map<String, Set<Integer>> currencyProductIndex = new ConcurrentHashMap<>();

    public void registerProductCurrency(Integer productId, String currency) {
        currencyProductIndex.computeIfAbsent(currency, k -> ConcurrentHashMap.newKeySet()).add(productId);
    }

    public void updateFxRate(FxRateDTO rate) {
        fxRates.put(rate.currencyPair(), rate);

        // --- RIPPLE LOGIC ---
        // If EURUSD changes, re-valuate all EUR products and all USD products
        String ccy1 = rate.currencyPair().substring(0, 3);
        String ccy2 = rate.currencyPair().substring(3, 6);

        triggerRipple(ccy1);
        triggerRipple(ccy2);
    }

    private void triggerRipple(String currency) {
        Set<Integer> affectedProducts = currencyProductIndex.get(currency);
        if (affectedProducts != null && !affectedProducts.isEmpty()) {
            // Trigger recalculation for every product linked to this currency
            affectedProducts.forEach(valuationService::recalculateAndPush);
        }
    }

    public BigDecimal getConversionRate(String fromCcy, String toCcy) {
        if (fromCcy.equals(toCcy)) return BigDecimal.ONE;

        String direct = fromCcy + toCcy;
        if (fxRates.containsKey(direct)) return fxRates.get(direct).rate();

        // Simplified fallback
        return BigDecimal.ONE;
    }
}