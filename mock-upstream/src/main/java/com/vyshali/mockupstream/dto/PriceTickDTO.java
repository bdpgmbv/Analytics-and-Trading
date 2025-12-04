package com.vyshali.mockupstream.dto;

/*
 * 12/03/2025 - 4:59 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;


// Added assetClass field to match Price Service
public record PriceTickDTO(Integer productId, String ticker, BigDecimal price, String currency, String assetClass,
                           // <--- NEW FIELD
                           Instant timestamp, String source) {
}