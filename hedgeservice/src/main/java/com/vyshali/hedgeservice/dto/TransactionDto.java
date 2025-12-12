package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 2: Transactions - Current day transactions.
 */
public record TransactionDto(
    Long transactionId,
    Integer portfolioId,
    String portfolioName,
    LocalDate transactionDate,
    String transactionType, // BUY, SELL, FX_HEDGE, DIVIDEND, etc.
    String ticker,
    String securityDescription,
    String currency,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal amount,
    BigDecimal amountBase,
    BigDecimal fxRate,
    String account,
    String counterparty,
    String status, // PENDING, EXECUTED, SETTLED, CANCELLED
    String externalRef,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime createdAt,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime executedAt
) {
    
    /**
     * Summary for all transactions in a portfolio.
     */
    public record TransactionSummary(
        Integer portfolioId,
        LocalDate asOfDate,
        java.util.List<TransactionDto> transactions,
        BigDecimal totalBuyAmount,
        BigDecimal totalSellAmount,
        BigDecimal netCashFlow,
        int transactionCount,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime lastUpdated
    ) {}
}
