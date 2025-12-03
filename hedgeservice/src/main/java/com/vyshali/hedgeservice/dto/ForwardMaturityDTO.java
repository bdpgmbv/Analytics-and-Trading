package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:11 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForwardMaturityDTO(String currency, BigDecimal currentNotional, BigDecimal unhedgedNotional,
                                 BigDecimal notionalHedgeCcy, LocalDate valueDate) {
}
