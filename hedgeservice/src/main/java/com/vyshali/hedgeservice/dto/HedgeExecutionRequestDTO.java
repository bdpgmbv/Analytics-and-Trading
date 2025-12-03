package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 1:46 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record HedgeExecutionRequestDTO(Integer fundId, String currencyPair, // e.g. "EURUSD"
                                       String side,         // "BUY", "SELL"
                                       BigDecimal quantity, String type,         // "SPOT", "FORWARD"
                                       String valueDate     // "2025-12-25"
) {
}
