package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:09 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record TransactionViewDTO(String source,              // "MSPA", "MSPM"
                                 String portfolioId,         // Account Number
                                 String identifierType,      // "CUSIP"
                                 String identifier,          // Value
                                 String securityDescription, String issueCcy, String settleCcy, String longShort,
                                 // "L" or "S"
                                 String buySell,             // "B" or "S"
                                 String positionType,        // "SECURITIES"
                                 BigDecimal quantity, BigDecimal costLocal, BigDecimal costSettle) {
}