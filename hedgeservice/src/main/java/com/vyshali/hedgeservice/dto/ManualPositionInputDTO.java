package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:10 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record ManualPositionInputDTO(Integer accountId, String ticker,          // User types "AAPL"
                                     String assetClass,      // "EQUITY", "CASH"
                                     BigDecimal quantity) {
}
