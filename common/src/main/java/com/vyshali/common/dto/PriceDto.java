package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for security price information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceDto {

    private Long priceId;
    private Long productId;
    
    // Security info
    private String identifier;
    private String ticker;
    private String securityDescription;
    
    // Price data
    private LocalDate priceDate;
    private LocalDateTime priceTime;
    private BigDecimal price;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    
    // Source (OVERRIDE, REALTIME, RCP_SNAP, MSPA)
    private String source;
    private Integer sourcePriority;
    
    // Status
    private Boolean isStale;
    
    // Currency
    private String currency;
}
