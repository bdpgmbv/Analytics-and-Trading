package com.vyshali.tradefillprocessor.dto;

/*
 * 12/03/2025 - 1:12 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record OrderStateDTO(String orderId, Integer accountId, String ticker, String side, BigDecimal totalFilledQty,
                            BigDecimal totalNotional, // sum(qty * price) used for VWAP
                            String status) {
}
