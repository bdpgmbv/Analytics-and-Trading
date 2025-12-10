package com.vyshali.positionloader.dto;

/*
 * 12/1/25 - 22:57
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Account snapshot from upstream MSPM system.
 */
public record AccountSnapshotDTO(@NotNull Integer accountId, @NotNull Integer clientId, String clientName,
                                 @NotNull Integer fundId, String fundName, String baseCurrency, String accountNumber,
                                 String accountType, @Valid List<PositionDTO> positions) {
    public int positionCount() {
        return positions != null ? positions.size() : 0;
    }

    public boolean isEmpty() {
        return positions == null || positions.isEmpty();
    }
}