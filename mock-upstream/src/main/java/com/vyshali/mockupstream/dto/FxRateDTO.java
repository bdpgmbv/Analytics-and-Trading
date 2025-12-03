package com.vyshali.mockupstream.dto;

/*
 * 12/03/2025 - 5:01 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;

public record FxRateDTO(String currencyPair, // e.g. "EURUSD"
                        BigDecimal rate, BigDecimal forwardPoints, Instant timestamp, String source) {
}
