package com.vyshali.positionloader.dto;

/*
 * 12/02/2025 - 11:10 AM
 * @author Vyshali Prabananth Lal
 */

import java.time.Instant;

public record ChangeEventDTO(String eventType, Integer accountId, Integer clientId, Integer positionCount,
                             Instant timestamp) {
}