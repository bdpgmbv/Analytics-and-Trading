package com.vyshali.priceservice.service;

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IMPROVED: FX cache with null-safety and configurable base currency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxCacheService {

    private final ApplicationEventPublisher publisher;

    @Value("${app.pricing.base-currency:USD}")
    private String baseCurrency;

    private final Map<String, FxRateDTO> fxRates = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> currencyProductIndex = new ConcurrentHashMap<>();

    public void registerProductCurrency(Integer productId, String currency) {
        if (productId == null || currency == null) return;
        currencyProductIndex
            .computeIfAbsent(currency.toUpperCase(), k -> ConcurrentHashMap.newKeySet())
            .add(productId);
    }

    public void updateFxRate(FxRateDTO rate) {
        if (rate == null || rate.currencyPair() == null) {
            log.warn("Attempted to update null FX rate");
            return;
        }
        
        String pair = rate.currencyPair().toUpperCase();
        fxRates.put(pair, rate);

        // Trigger ripple for affected products
        if (pair.length() >= 6) {
            triggerRipple(pair.substring(0, 3));
            triggerRipple(pair.substring(3, 6));
        }
    }

    private void triggerRipple(String currency) {
        Set<Integer> affected = currencyProductIndex.get(currency);
        if (affected != null && !affected.isEmpty()) {
            publisher.publishEvent(new FxRippleEvent(Set.copyOf(affected)));
        }
    }

    /**
     * Get conversion rate with null-safety and cross-rate support.
     */
    public BigDecimal getConversionRate(String fromCcy, String toCcy) {
        // Null safety
        if (fromCcy == null || toCcy == null) {
            log.warn("Null currency in conversion: from={}, to={}", fromCcy, toCcy);
            return BigDecimal.ONE;
        }
        
        String from = fromCcy.toUpperCase();
        String to = toCcy.toUpperCase();
        
        // Same currency = 1:1
        if (from.equals(to)) return BigDecimal.ONE;
        
        // Direct rate: FROM/TO
        String direct = from + to;
        if (fxRates.containsKey(direct)) {
            return fxRates.get(direct).rate();
        }
        
        // Inverse rate: TO/FROM
        String inverse = to + from;
        if (fxRates.containsKey(inverse)) {
            BigDecimal inverseRate = fxRates.get(inverse).rate();
            if (inverseRate.compareTo(BigDecimal.ZERO) != 0) {
                return BigDecimal.ONE.divide(inverseRate, 8, RoundingMode.HALF_UP);
            }
        }
        
        // Cross rate via base currency (e.g., EUR->GBP via USD)
        String fromBase = from + baseCurrency;
        String toBase = to + baseCurrency;
        if (fxRates.containsKey(fromBase) && fxRates.containsKey(toBase)) {
            BigDecimal fromRate = fxRates.get(fromBase).rate();
            BigDecimal toRate = fxRates.get(toBase).rate();
            if (toRate.compareTo(BigDecimal.ZERO) != 0) {
                return fromRate.divide(toRate, 8, RoundingMode.HALF_UP);
            }
        }
        
        log.warn("No FX rate found for {}/{}, defaulting to 1.0", from, to);
        return BigDecimal.ONE;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public record FxRippleEvent(Set<Integer> affectedProducts) {}
}
