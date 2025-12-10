package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 12:50 PM
 * @author Vyshali Prabananth Lal
 */

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound event DTOs for Kafka publishing.
 */
public final class Events {

    private Events() {
    } // Utility class

    /**
     * Published when positions change (EOD or Intraday).
     */
    public record PositionChange(String eventType,  // EOD_COMPLETE, INTRADAY_UPDATE
                                 Integer accountId, Integer clientId, int positionCount, Instant timestamp) {
        public PositionChange(String eventType, Integer accountId, Integer clientId, int positionCount) {
            this(eventType, accountId, clientId, positionCount, Instant.now());
        }
    }

    /**
     * Published when all accounts for a client complete EOD.
     */
    public record ClientSignOff(Integer clientId, LocalDate businessDate, String status, int totalAccounts) {
        public ClientSignOff(Integer clientId, LocalDate businessDate, int totalAccounts) {
            this(clientId, businessDate, "READY_FOR_REPORTING", totalAccounts);
        }
    }

    /**
     * Single position quantity change event.
     */
    public record QuantityChange(Integer accountId, Integer productId, BigDecimal newQuantity, Instant timestamp) {
        public QuantityChange(Integer accountId, Integer productId, BigDecimal newQuantity) {
            this(accountId, productId, newQuantity, Instant.now());
        }
    }
}
