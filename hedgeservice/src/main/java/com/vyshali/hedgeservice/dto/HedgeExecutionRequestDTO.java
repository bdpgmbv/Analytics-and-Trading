package com.vyshali.hedgeservice.dto;

/*
 * 12/03/2025 - 1:46 PM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record HedgeExecutionRequestDTO(@NotNull(message = "Account ID is required") Integer accountId,

                                       @NotBlank(message = "Currency Pair is required") @Pattern(regexp = "[A-Z]{3}/[A-Z]{3}", message = "Currency Pair must be in format 'USD/EUR'") String currencyPair,

                                       @NotNull(message = "Quantity is required") @DecimalMin(value = "0.01", message = "Quantity must be greater than zero") BigDecimal quantity,

                                       @NotBlank(message = "Side is required") @Pattern(regexp = "BUY|SELL", message = "Side must be 'BUY' or 'SELL'") String side,

                                       @NotBlank(message = "Tenor is required") String tenor) {
}