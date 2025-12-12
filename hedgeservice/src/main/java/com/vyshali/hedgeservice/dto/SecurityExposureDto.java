package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 1: Security Exposure - Real-time currency exposures and P/L.
 */
public record SecurityExposureDto(
    Integer portfolioId,
    String portfolioName,
    String baseCurrency,
    LocalDate asOfDate,
    
    // Exposure breakdown by currency
    java.util.List<CurrencyExposure> currencyExposures,
    
    // Summary totals
    BigDecimal totalMarketValue,
    BigDecimal totalUnrealizedPnl,
    BigDecimal totalRealizedPnl,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime lastUpdated
) {
    
    public record CurrencyExposure(
        String currency,
        BigDecimal marketValue,
        BigDecimal marketValueBase,
        BigDecimal quantity,
        BigDecimal fxRate,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        BigDecimal netExposure,
        BigDecimal hedgeRatio,
        
        // Position details
        java.util.List<PositionDetail> positions
    ) {}
    
    public record PositionDetail(
        Long positionId,
        String ticker,
        String securityDescription,
        String assetClass,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal marketValue,
        BigDecimal marketValueBase,
        BigDecimal unrealizedPnl,
        BigDecimal costBasis,
        String account
    ) {}
}
