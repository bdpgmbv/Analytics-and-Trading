package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for Trade Execution tracking
 * Addresses Issue #2: No tracking for executed trades (sent vs executed)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutionDto {

    private Long executionId;
    private String executionRef;       // FX Matrix reference
    
    // Account info
    private String accountNumber;
    private String fundCode;
    
    // Trade details
    private String tradeType;          // SPOT, FORWARD, SWAP
    private String buyCurrency;
    private String sellCurrency;
    private BigDecimal buyAmount;
    private BigDecimal sellAmount;
    private BigDecimal executionRate;
    
    // Dates
    private LocalDate valueDate;
    private LocalDateTime sentAt;
    private LocalDateTime executedAt;
    
    // Status (SENT, EXECUTED, REJECTED, FAILED)
    private String status;
    private String errorMessage;
    
    // Source info
    private String sourceTab;          // Which UI tab originated this trade
    
    // Counterparty
    private String counterpartyCode;
    private String counterpartyName;
    
    // Calculated
    private Long durationMillis;       // Time from sent to executed
    private Boolean isSuccessful;
}
