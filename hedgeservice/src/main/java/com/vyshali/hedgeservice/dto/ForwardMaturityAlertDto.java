package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 5: Forward Maturity Alert - Alerts for maturing forward contracts.
 */
public record ForwardMaturityAlertDto(
    Integer portfolioId,
    String portfolioName,
    LocalDate asOfDate,
    
    // Alerts grouped by urgency
    java.util.List<ForwardAlert> criticalAlerts,   // Maturing within 2 days
    java.util.List<ForwardAlert> warningAlerts,    // Maturing within 7 days
    java.util.List<ForwardAlert> informationalAlerts, // Maturing within 30 days
    
    // Summary
    int totalAlertsCount,
    BigDecimal totalNotionalMaturing7Days,
    BigDecimal totalNotionalMaturing30Days,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime lastUpdated
) {
    
    public record ForwardAlert(
        Long forwardContractId,
        String contractRef,
        AlertLevel alertLevel,
        int daysToMaturity,
        
        // Contract details
        LocalDate tradeDate,
        LocalDate maturityDate,
        String buyCurrency,
        String sellCurrency,
        BigDecimal notionalAmount,
        BigDecimal forwardRate,
        BigDecimal spotRate,
        BigDecimal currentMtmPnl,
        
        // Settlement details
        String settlementAccount,
        BigDecimal settlementAmountBuy,
        BigDecimal settlementAmountSell,
        
        // Action recommendations
        String counterparty,
        String recommendedAction, // ROLL_FORWARD, SETTLE, CLOSE_OUT
        boolean autoRollEnabled,
        
        // Related info
        String relatedHedge,
        String notes
    ) {}
    
    public enum AlertLevel {
        CRITICAL,   // 0-2 days to maturity
        WARNING,    // 3-7 days to maturity
        INFORMATIONAL // 8-30 days to maturity
    }
}
