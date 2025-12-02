package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PositionDetailDTO(@NotNull Integer productId, String ticker, String assetClass, String issueCurrency,
                                @NotNull BigDecimal quantity, String txnType // BUY, SELL
) {
}