package com.vyshali.mockupstream.dto;

/*
 * 12/03/2025 - 4:53 PM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ExecutionReportDTO(@NotBlank(message = "Execution ID is mandatory") String execID,

                                 @NotBlank String orderID, @NotBlank String symbol, @NotBlank String side,

                                 @NotNull @Positive BigDecimal lastQty,

                                 @NotNull @Positive BigDecimal lastPx) {
}