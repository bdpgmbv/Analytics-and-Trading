package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FxCacheService {
    private final Map<String, FxRateDTO> fxRates = new ConcurrentHashMap<>();

    public void updateFxRate(FxRateDTO rate) {
        fxRates.put(rate.currencyPair(), rate);
    }

    public BigDecimal getConversionRate(String fromCcy, String toCcy) {
        if (fromCcy.equals(toCcy)) return BigDecimal.ONE;
        String direct = fromCcy + toCcy;
        if (fxRates.containsKey(direct)) return fxRates.get(direct).rate();
        // Simplified fallback
        return BigDecimal.ONE;
    }
}
