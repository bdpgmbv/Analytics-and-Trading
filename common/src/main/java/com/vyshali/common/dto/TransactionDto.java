package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Tab 2 - Transactions (getTransactions endpoint)
 * Maps to: Source, Portfolio Id, Identifier Type, CUSIP, Security Description,
 *          Issue Ccy, Settle Ccy, L/S, B/S, position type, Quantity, Cost local, Cost Settle
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {

    private Long transactionId;
    
    // Source and Portfolio
    private String source;           // MSPA, MANUAL
    private String portfolioId;      // Account number
    
    // Security identification
    private String identifierType;
    private String identifier;       // CUSIP
    private String securityDescription;
    
    // Currency info
    private String issueCurrency;
    private String settlementCurrency;
    
    // Trade details
    private String longShort;        // L or S
    private String buySell;          // B or S (transaction type)
    private String positionType;
    
    // Quantity and cost
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal costLocal;
    private BigDecimal costSettle;
    
    // Dates
    private LocalDate tradeDate;
    private LocalDate settleDate;
    
    // Status
    private String status;
    
    // Flag indicating if this is today's trade (highlighted in green in UI)
    private Boolean isCurrentDayTrade;
}
