package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:50 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;

public record FxRateDTO(String currencyPair, BigDecimal rate, BigDecimal forwardPoints, Instant timestamp,
                        String source) {
}
