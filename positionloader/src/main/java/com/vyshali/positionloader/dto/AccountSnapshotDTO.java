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
                                 String accountType, @Valid List<PositionDetailDTO> positions, String status
                                 // Added for fallback handling: "OK", "Unavailable", "ERROR"
) {
    // Convenience constructor for backward compatibility (without status)
    public AccountSnapshotDTO(Integer clientId, String clientName, Integer fundId, String fundName, String baseCurrency, Integer accountId, String accountNumber, String accountType, List<PositionDetailDTO> positions) {
        this(clientId, clientName, fundId, fundName, baseCurrency, accountId, accountNumber, accountType, positions, "OK");
    }
}