package com.vyshali.positionloader.dto;

/*
 * 12/04/2025 - 10:46 AM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record TradeEventDTO(@NotBlank(message = "Transaction ID is required") String transactionId,

                            String originalRefId, // Optional (only for Amend/Cancel)

                            @NotBlank(message = "Event Type (NEW/AMEND/CANCEL) is required") String eventType,

                            @NotNull(message = "Account ID is mandatory") Integer accountId,

                            Integer clientId,

                            @Valid // Cascades validation to the list items
                            List<PositionDetail> positions) {
    public record PositionDetail(@NotNull Integer productId, @NotBlank String ticker,

                                 @NotNull @Positive(message = "Quantity must be positive") // Fail fast on -100
                                 BigDecimal quantity,

                                 @NotBlank String txnType, // BUY/SELL

                                 @Positive BigDecimal price) {
    }
}