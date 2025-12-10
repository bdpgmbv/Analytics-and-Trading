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
                                 String accountType, @Valid List<PositionDTO> positions, String status
                                 // OK, STALE_CACHE, UNAVAILABLE, ERROR
) {
    // Convenience constructor without status (defaults to OK)
    public AccountSnapshotDTO(Integer accountId, Integer clientId, String clientName, Integer fundId, String fundName, String baseCurrency, String accountNumber, String accountType, List<PositionDTO> positions) {
        this(accountId, clientId, clientName, fundId, fundName, baseCurrency, accountNumber, accountType, positions, "OK");
    }

    public boolean isAvailable() {
        return !"UNAVAILABLE".equals(status) && !"ERROR".equals(status);
    }

    public boolean isStale() {
        return "STALE_CACHE".equals(status);
    }

    public int positionCount() {
        return positions != null ? positions.size() : 0;
    }
}