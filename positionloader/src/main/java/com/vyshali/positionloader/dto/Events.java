package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 12:50 PM
 * @author Vyshali Prabananth Lal
 */

import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound Kafka events.
 */
public final class Events {

    private Events() {
    }

    /**
     * Published when positions change (EOD or Intraday).
     */
    public record PositionChange(String eventType, Integer accountId, Integer clientId, int positionCount,
                                 Instant timestamp) {
        public PositionChange(String eventType, Integer accountId, Integer clientId, int positionCount) {
            this(eventType, accountId, clientId, positionCount, Instant.now());
        }
    }

    /**
     * Published when all accounts for a client complete EOD.
     */
    public record ClientSignOff(Integer clientId, LocalDate businessDate, int totalAccounts, Instant timestamp) {
        public ClientSignOff(Integer clientId, LocalDate businessDate, int totalAccounts) {
            this(clientId, businessDate, totalAccounts, Instant.now());
        }
    }
}