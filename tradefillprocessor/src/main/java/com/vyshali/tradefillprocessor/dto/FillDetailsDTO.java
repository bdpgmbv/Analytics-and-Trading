package com.vyshali.tradefillprocessor.dto;

/*
 * 12/03/2025 - 1:11 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FillDetailsDTO(String execId, BigDecimal fillQty, BigDecimal fillPrice, LocalDateTime fillTime,
                             String venue) {
}
