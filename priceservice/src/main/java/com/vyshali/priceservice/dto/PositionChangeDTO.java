package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:50 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionChangeDTO(Integer accountId, Integer productId, BigDecimal newQuantity) {
}
