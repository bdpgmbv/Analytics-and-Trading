package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AccountSnapshotDTO(@NotNull Integer clientId, String clientName, @NotNull Integer fundId, String fundName,
                                 String baseCurrency, @NotNull Integer accountId, String accountNumber,
                                 String accountType, @Valid List<PositionDetailDTO> positions) {
}