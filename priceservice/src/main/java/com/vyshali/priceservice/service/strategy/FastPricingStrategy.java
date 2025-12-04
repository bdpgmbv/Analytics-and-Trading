package com.vyshali.priceservice.service.strategy;

/*
 * 12/04/2025 - 2:37 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FastPricingStrategy implements PricingStrategy {

    private static final long MICROS = 1_000_000L;

    @Override
    public boolean supports(String assetClass) {
        return "EQUITY".equalsIgnoreCase(assetClass) || "FX".equalsIgnoreCase(assetClass);
    }

    @Override
    public BigDecimal calculateMarketValue(BigDecimal quantity, PriceTickDTO tick, BigDecimal fxRate) {
        // 1. Convert to primitives (micros)
        long qtyMicros = quantity.multiply(BigDecimal.valueOf(MICROS)).longValue();

        // ERROR FIX: Ensure this calls .price() (Record accessor) not .getPrice()
        long priceMicros = tick.price().multiply(BigDecimal.valueOf(MICROS)).longValue();

        long fxMicros = fxRate.multiply(BigDecimal.valueOf(MICROS)).longValue();

        // 2. Perform Math (M^3 / M^2 = M)
        long marketValueMicros = (qtyMicros * priceMicros) / MICROS;
        long finalValueMicros = (marketValueMicros * fxMicros) / MICROS;

        // 3. Convert back
        return BigDecimal.valueOf(finalValueMicros).divide(BigDecimal.valueOf(MICROS), 6, RoundingMode.HALF_UP);
    }
}