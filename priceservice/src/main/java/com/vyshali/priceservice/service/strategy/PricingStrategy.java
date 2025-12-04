package com.vyshali.priceservice.service.strategy;

/*
 * 12/04/2025 - 1:38 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;

import java.math.BigDecimal;

public interface PricingStrategy {
    boolean supports(String assetClass);

    BigDecimal calculateMarketValue(BigDecimal quantity, PriceTickDTO tick, BigDecimal fxRate);
}