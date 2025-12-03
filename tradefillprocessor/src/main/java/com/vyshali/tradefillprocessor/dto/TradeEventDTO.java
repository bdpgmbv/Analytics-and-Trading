package com.vyshali.tradefillprocessor.dto;

/*
 * 12/03/2025 - 1:12 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.util.List;

public record TradeEventDTO(Integer accountId, Integer clientId, List<PositionDetail> positions) {
    public record PositionDetail(Integer productId, String ticker, BigDecimal quantity, String txnType, BigDecimal price
                                 // Final Avg Price
    ) {
    }
}
