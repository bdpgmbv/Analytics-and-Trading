package com.vyshali.tradefillprocessor.dto;

/*
 * 12/03/2025 - 1:11 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record ExecutionReportDTO(String orderId, String execId, Integer accountId, String ticker, String side,
                                 // "BUY", "SELL"
                                 BigDecimal lastQty, // Qty of THIS fill
                                 BigDecimal lastPx,  // Price of THIS fill
                                 String status,      // "PARTIALLY_FILLED", "FILLED"
                                 String venue) {
}
