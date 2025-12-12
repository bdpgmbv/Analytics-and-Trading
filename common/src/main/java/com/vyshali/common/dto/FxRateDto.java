package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for FX rate information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxRateDto {

    private Long fxRateId;
    
    // Currency pair
    private String currencyPair;      // e.g., "EURUSD"
    private String baseCurrency;      // e.g., "EUR"
    private String quoteCurrency;     // e.g., "USD"
    
    // Rate data
    private LocalDate rateDate;
    private LocalDateTime rateTime;
    private BigDecimal midRate;
    private BigDecimal bidRate;
    private BigDecimal askRate;
    
    // Forward points
    private BigDecimal forwardPoints1M;
    private BigDecimal forwardPoints3M;
    
    // Source
    private String source;
    
    // Status
    private Boolean isStale;
    
    /**
     * Calculate forward rate for 1 month
     */
    public BigDecimal getForwardRate1M() {
        if (midRate != null && forwardPoints1M != null) {
            return midRate.add(forwardPoints1M);
        }
        return midRate;
    }
    
    /**
     * Calculate forward rate for 3 months
     */
    public BigDecimal getForwardRate3M() {
        if (midRate != null && forwardPoints3M != null) {
            return midRate.add(forwardPoints3M);
        }
        return midRate;
    }
}
