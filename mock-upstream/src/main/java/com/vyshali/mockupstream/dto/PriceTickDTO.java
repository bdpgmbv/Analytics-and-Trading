package com.vyshali.mockupstream.dto;

/*
 * 12/03/2025 - 4:59 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTickDTO(Integer productId, String ticker, BigDecimal price, String currency, Instant timestamp,
                           String source) {
}
