package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:49 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;

// TIER 1 ARCHITECTURE: Using Java Record for immutability
// If this were a 'class', you would need .getPrice() instead of .price()
public record PriceTickDTO(Integer productId, String ticker, BigDecimal price, String currency, String assetClass,
                           // Ensure this field exists for the Strategy pattern
                           Instant timestamp, String source) {
}