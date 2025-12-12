package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for Tab 3 - Share Class Management
 * Manages investor subscription/redemption currency exposures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareClassDto {

    private Long shareClassId;
    private Long fundId;
    
    // Share class info
    private String shareClassCode;
    private String shareClassName;
    private String currency;
    
    // Values
    private BigDecimal nav;
    private BigDecimal performanceAdjustment;
    private BigDecimal subscriptionRedemption;
    private BigDecimal netExposure;
    
    // Status
    private String status;
    
    // Fund info (denormalized for display)
    private String fundCode;
    private String fundName;
    private String baseCurrency;
}
