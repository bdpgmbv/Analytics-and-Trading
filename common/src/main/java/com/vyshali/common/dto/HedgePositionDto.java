package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Tab 1 - Security Exposure (getHedgePositions endpoint)
 * Maps to: Identifier Type, CUSIP, position type, Issue Ccy, 
 *          genericExposure Currency/Weight%, specificExposure Currency/Weight%, 
 *          FX rate, price, Quantity, MV Base, MV Local
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HedgePositionDto {

    private Long positionId;
    
    // Security identification
    private String identifierType;
    private String identifier;       // CUSIP, ISIN, etc.
    private String ticker;
    private String securityDescription;
    
    // Position details
    private String positionType;
    private String issueCurrency;
    private String settlementCurrency;
    
    // Quantity and values
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal fxRate;
    private BigDecimal marketValueLocal;
    private BigDecimal marketValueBase;
    private BigDecimal unrealizedPnlBase;
    
    // Currency exposures
    private List<ExposureDto> genericExposures;
    private List<ExposureDto> specificExposures;
    
    // Flags
    private Boolean isExcluded;
    private Boolean isLong;
    
    /**
     * Nested DTO for currency exposure breakdown
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExposureDto {
        private String currency;
        private BigDecimal weightPercent;
        private BigDecimal exposureAmount;
    }
}
