package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 12:11 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record HedgePositionDTO(String identifierType, String identifier, String positionType, String issueCcy,

                               // Exposures (Aggregated from Position_Exposures table)
                               String genericExposureCurrency, BigDecimal genericExposureWeight,
                               String specificExposureCurrency, BigDecimal specificExposureWeight,

                               // Valuation
                               BigDecimal fxRate, BigDecimal price, BigDecimal quantity, BigDecimal mvBase,
                               BigDecimal mvLocal) {
}
