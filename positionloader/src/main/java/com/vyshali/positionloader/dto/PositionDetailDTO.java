package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionDetailDTO(Integer productId, String ticker, String assetClass, String issueCurrency,
                                BigDecimal quantity, String txnType, BigDecimal price, String externalRefId
                                // <--- NEW FIELD
) {
}