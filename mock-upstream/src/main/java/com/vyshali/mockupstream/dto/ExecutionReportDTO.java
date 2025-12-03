package com.vyshali.mockupstream.dto;

/*
 * 12/03/2025 - 4:53 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

// This DTO represents a "Fill" message sent from the Exchange
public record ExecutionReportDTO(String orderId,     // The Client's Order ID (e.g. "ORD-101")
                                 String execId,      // The Exchange's Execution ID (e.g. "EXEC-99")
                                 Integer accountId,  // Which account bought it
                                 String ticker,      // "AAPL"
                                 String side,        // "BUY" or "SELL"
                                 BigDecimal lastQty, // Quantity of THIS specific fill (e.g. 100)
                                 BigDecimal lastPx,  // Price of THIS specific fill (e.g. 150.50)
                                 String status,      // "PARTIALLY_FILLED" or "FILLED"
                                 String venue        // "NYSE", "NASDAQ"
) {
}