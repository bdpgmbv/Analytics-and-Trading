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

/**
 * Trade event from FX Analyzer for intraday position updates.
 */
public record TradeEventDTO(@NotBlank String transactionId, @NotBlank String eventType,  // BUY, SELL, CANCEL, AMEND
                            @NotNull Integer accountId, Integer clientId, String originalRefId,  // For AMEND/CANCEL
                            @Valid List<TradeLeg> legs) {
    /**
     * Single leg of a trade.
     */
    public record TradeLeg(@NotNull Integer productId, @NotBlank String ticker, @NotNull @Positive BigDecimal quantity,
                           @NotBlank String side,  // BUY, SELL
                           BigDecimal price) {
    }

    public boolean isCancel() {
        return "CANCEL".equalsIgnoreCase(eventType);
    }

    public boolean isAmend() {
        return "AMEND".equalsIgnoreCase(eventType);
    }
}