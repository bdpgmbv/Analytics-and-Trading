package com.vyshali.mockupstream.dto;

/*
 * 12/02/2025 - 2:02 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionDetailDTO(Integer productId, String ticker, String assetClass, String issueCurrency,
                                BigDecimal quantity, String txnType, BigDecimal price, String externalRefId
                                // <--- NEW FIELD
) {
}
