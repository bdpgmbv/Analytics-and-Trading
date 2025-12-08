package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FxCacheService {

    // Decoupled: Uses Events instead of direct ValuationService dependency
    private final ApplicationEventPublisher publisher;

    // Local Hot Cache for FX
    private final Map<String, FxRateDTO> fxRates = new ConcurrentHashMap<>();

    // REVERSE INDEX: Currency -> List of Products
    private final Map<String, Set<Integer>> currencyProductIndex = new ConcurrentHashMap<>();

    public void registerProductCurrency(Integer productId, String currency) {
        currencyProductIndex.computeIfAbsent(currency, k -> ConcurrentHashMap.newKeySet()).add(productId);
    }

    public void updateFxRate(FxRateDTO rate) {
        fxRates.put(rate.currencyPair(), rate);

        // --- RIPPLE LOGIC ---
        String ccy1 = rate.currencyPair().substring(0, 3);
        String ccy2 = rate.currencyPair().substring(3, 6);

        triggerRipple(ccy1);
        triggerRipple(ccy2);
    }

    private void triggerRipple(String currency) {
        Set<Integer> affectedProducts = currencyProductIndex.get(currency);
        if (affectedProducts != null && !affectedProducts.isEmpty()) {
            // Publish Event to break circular dependency
            publisher.publishEvent(new FxRippleEvent(affectedProducts));
        }
    }

    public BigDecimal getConversionRate(String fromCcy, String toCcy) {
        if (fromCcy.equals(toCcy)) return BigDecimal.ONE;
        String direct = fromCcy + toCcy;
        if (fxRates.containsKey(direct)) return fxRates.get(direct).rate();
        return BigDecimal.ONE;
    }

    // Inner Domain Event
    public record FxRippleEvent(Set<Integer> affectedProducts) {}
}