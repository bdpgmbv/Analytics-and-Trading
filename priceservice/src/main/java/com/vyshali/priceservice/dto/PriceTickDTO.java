package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:49 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;

// Added assetClass field
public record PriceTickDTO(Integer productId, String ticker, BigDecimal price, String currency, String assetClass,
                           // <--- NEW FIELD
                           Instant timestamp, String source) {
}