package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 3: Share Class - Share class currency hedging.
 */
public record ShareClassDto(
    Integer fundId,
    String fundName,
    String baseCurrency,
    LocalDate asOfDate,
    
    java.util.List<ShareClass> shareClasses,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime lastUpdated
) {
    
    public record ShareClass(
        Integer shareClassId,
        String shareClassName,
        String shareCurrency,
        BigDecimal nav,
        BigDecimal navBase,
        BigDecimal sharesOutstanding,
        BigDecimal totalAssets,
        BigDecimal totalAssetsBase,
        
        // Hedging details
        HedgingStrategy hedgingStrategy,
        BigDecimal currencyExposure,
        BigDecimal hedgedAmount,
        BigDecimal unhedgedAmount,
        BigDecimal hedgeRatio,
        BigDecimal targetHedgeRatio,
        BigDecimal hedgeVariance,
        
        // Performance
        BigDecimal monthToDateReturn,
        BigDecimal yearToDateReturn,
        BigDecimal inceptionReturn,
        
        // Active hedges
        java.util.List<ActiveHedge> activeHedges
    ) {}
    
    public record HedgingStrategy(
        String strategyType, // FULLY_HEDGED, PARTIALLY_HEDGED, UNHEDGED, DYNAMIC
        BigDecimal targetHedgeRatio,
        BigDecimal minHedgeRatio,
        BigDecimal maxHedgeRatio,
        int rebalanceFrequencyDays,
        BigDecimal rebalanceThreshold
    ) {}
    
    public record ActiveHedge(
        Long hedgeId,
        String hedgeType, // FX_FORWARD, FX_SWAP, FX_OPTION
        String buyCurrency,
        String sellCurrency,
        BigDecimal notionalAmount,
        BigDecimal hedgeRate,
        LocalDate tradeDate,
        LocalDate maturityDate,
        String status,
        BigDecimal markToMarketPnl
    ) {}
}
