package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:49 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record ValuationDTO(Integer accountId, Integer productId, BigDecimal marketValueBase, BigDecimal priceUsed,
                           BigDecimal fxRateUsed, String status) {
}