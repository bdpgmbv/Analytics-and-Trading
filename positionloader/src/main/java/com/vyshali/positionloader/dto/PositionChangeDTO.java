package com.vyshali.positionloader.dto;

/*
 * 12/08/2025 - 9:32 AM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;

public record PositionChangeDTO(Integer accountId, Integer productId, BigDecimal newQuantity) {
}
