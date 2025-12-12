package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 4: Cash Management - Currency cash balances and cash flows.
 */
public record CashManagementDto(
    Integer portfolioId,
    String portfolioName,
    String baseCurrency,
    LocalDate asOfDate,
    
    // Cash balances by currency
    java.util.List<CashBalance> cashBalances,
    
    // Projected cash flows
    java.util.List<CashFlow> projectedCashFlows,
    
    // Summary
    BigDecimal totalCashBase,
    BigDecimal projectedNetCashFlow7Days,
    BigDecimal projectedNetCashFlow30Days,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime lastUpdated
) {
    
    public record CashBalance(
        String currency,
        BigDecimal balance,
        BigDecimal balanceBase,
        BigDecimal fxRate,
        String account,
        BigDecimal dayChangeAmount,
        BigDecimal dayChangePercent,
        
        // Interest/Yield info
        BigDecimal interestRate,
        BigDecimal accruedInterest,
        
        // Limits
        BigDecimal minBalanceRequired,
        BigDecimal maxBalanceAllowed,
        boolean isOverdrawn,
        boolean breachesLimit
    ) {}
    
    public record CashFlow(
        String flowType, // EXPECTED_SETTLEMENT, DIVIDEND, COUPON, FX_FORWARD_SETTLEMENT, REDEMPTION, SUBSCRIPTION
        LocalDate valueDate,
        String currency,
        BigDecimal amount,
        BigDecimal amountBase,
        String description,
        String relatedTransaction,
        String status // PROJECTED, CONFIRMED
    ) {}
}
