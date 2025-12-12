package com.vyshali.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Tab 4 - Cash Management (getCashManagement endpoint)
 * Maps to: currency, cashBalance, Unhedge Exposure, Trade Type, 
 *          Spot Amount(Hedge Ccy), Forward Amount(Hedge Ccy), Value Date
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashManagementDto {

    private Long cashBalanceId;
    private String accountNumber;
    
    // Currency and balance
    private String currency;
    private BigDecimal cashBalance;
    
    // Exposure
    private BigDecimal unhedgedExposure;
    
    // Hedge trade details
    private String tradeType;           // SPOT, FORWARD, SPOT_AND_FORWARD
    private BigDecimal spotAmount;      // Spot Amount in Hedge Currency
    private BigDecimal forwardAmount;   // Forward Amount in Hedge Currency
    private LocalDate valueDate;
    
    // Balance date
    private LocalDate balanceDate;
    
    // Calculated fields
    private BigDecimal hedgedAmount;    // spotAmount + forwardAmount
    private BigDecimal netExposure;     // unhedgedExposure - hedgedAmount
}
