package com.vyshali.mockupstream.dto;

/*
 * 12/02/2025 - 2:01 PM
 * @author Vyshali Prabananth Lal
 */

import java.util.List;

public record AccountSnapshotDTO(
        Integer clientId,
        String clientName,
        Integer fundId,
        String fundName,
        String baseCurrency,
        Integer accountId,
        String accountNumber,
        String accountType,
        List<PositionDetailDTO> positions
) {}
