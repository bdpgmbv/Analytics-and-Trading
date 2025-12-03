package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:11 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashManagementDTO(String currency, BigDecimal cashBalance, BigDecimal unhedgedExposure, String tradeType,
                                // "SPOT", "FORWARD"
                                BigDecimal spotAmountHedgeCcy, BigDecimal forwardAmountHedgeCcy, LocalDate valueDate) {
}
