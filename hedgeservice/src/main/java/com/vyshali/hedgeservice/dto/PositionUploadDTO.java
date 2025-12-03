package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:10 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionUploadDTO(String source,              // "MANUAL"
                                String portfolioId, String identifierType, String identifier,
                                String securityDescription, String issueCcy, String settleCcy, String positionType,
                                BigDecimal quantity, BigDecimal mvBase) {
}
