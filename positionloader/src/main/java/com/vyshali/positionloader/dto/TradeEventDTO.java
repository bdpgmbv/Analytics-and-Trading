package com.vyshali.positionloader.dto;

/*
 * 12/04/2025 - 10:46 AM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.util.List;

public record TradeEventDTO(Integer accountId, Integer clientId, List<PositionDetail> positions) {
    public record PositionDetail(Integer productId, String ticker, BigDecimal quantity, String txnType,
                                 BigDecimal price) {
    }
}
