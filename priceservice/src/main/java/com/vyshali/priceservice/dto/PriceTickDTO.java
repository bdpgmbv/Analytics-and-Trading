package com.vyshali.priceservice.dto;

/*
 * 12/02/2025 - 6:49 PM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTickDTO(@NotNull(message = "Product ID required") Integer productId,

                           @NotBlank(message = "Ticker required") String ticker,

                           @NotNull @Positive(message = "Price must be positive") BigDecimal price,

                           @NotBlank String currency, String assetClass, Instant timestamp, String source) {
}