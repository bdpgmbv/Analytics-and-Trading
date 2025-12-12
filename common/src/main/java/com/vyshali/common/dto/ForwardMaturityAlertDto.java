package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Tab 5 - Forward Maturity Alert (getForwardMaturityAlert endpoint)
 * Maps to: Currency, currentNotional, unhedgedNotional, Notional(Hedge Ccy), Value Date
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwardMaturityAlertDto {

    private Long forwardId;
    private String accountNumber;
    
    // Currency pair
    private String buyCurrency;
    private String sellCurrency;
    private String currencyPair;       // e.g., "EUR/USD"
    
    // Notional amounts
    private BigDecimal currentNotional;
    private BigDecimal unhedgedNotional;
    private BigDecimal notionalHedgeCcy; // Notional in hedge currency
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    
    // Rate
    private BigDecimal strikeRate;
    
    // Dates
    private LocalDate tradeDate;
    private LocalDate valueDate;
    
    // Maturity info
    private Integer daysToMaturity;
    
    // Status
    private String status;
    
    // Alert flags
    private Boolean isNearMaturity;     // e.g., < 7 days
    private Boolean isOverdue;          // maturity date has passed
    
    // Counterparty
    private String counterpartyCode;
    private String counterpartyName;
}
