package com.vyshali.mockupstream.dto;

/*
 * 12/02/2025 - 2:02 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionDetailDTO(Integer productId,      // 1
                                String ticker,          // 2
                                String assetClass,      // 3
                                String issueCurrency,   // 4
                                BigDecimal quantity,    // 5
                                String txnType,         // 6 (This is likely the missing one causing the error)
                                BigDecimal price        // 7
) {
}
