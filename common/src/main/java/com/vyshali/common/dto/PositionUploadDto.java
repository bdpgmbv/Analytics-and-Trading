package com.vyshali.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for Tab 6 - Position Upload (positionUpload endpoint)
 * Maps to: source, portfolio id, Identifier Type, CUSIP, Security Description,
 *          Issue Ccy, Settle Ccy, position type, Quantity, MV Base
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionUploadDto {

    // Source (MANUAL or FTP)
    @NotBlank(message = "Source is required")
    private String source;
    
    // Portfolio/Account
    @NotBlank(message = "Portfolio ID is required")
    private String portfolioId;    // Account number
    
    // Security identification
    @NotBlank(message = "Identifier type is required")
    private String identifierType;
    
    @NotBlank(message = "Identifier is required")
    private String identifier;     // CUSIP, ISIN, etc.
    
    private String securityDescription;
    
    // Currency info
    @NotBlank(message = "Issue currency is required")
    private String issueCurrency;
    
    @NotBlank(message = "Settlement currency is required")
    private String settlementCurrency;
    
    // Position details
    private String positionType;
    
    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;
    
    @NotNull(message = "Market value base is required")
    private BigDecimal marketValueBase;
    
    // Optional fields
    private BigDecimal marketValueLocal;
    private BigDecimal price;
    private BigDecimal fxRate;
}
