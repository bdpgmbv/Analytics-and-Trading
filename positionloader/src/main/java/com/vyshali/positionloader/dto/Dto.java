package com.vyshali.positionloader.dto;

/*
 * 12/11/2025 - 11:43 AM
 * @author Vyshali Prabananth Lal
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All DTOs in one file - simple and easy to find.
 */
public final class Dto {
    private Dto() {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION
    // ═══════════════════════════════════════════════════════════════════════════
    public record Position(@NotNull Integer productId, String ticker, String assetClass, String currency,
                           @NotNull BigDecimal quantity, BigDecimal price) {
        public Position {
            if (quantity == null) quantity = BigDecimal.ZERO;
            if (price == null) price = BigDecimal.ZERO;
            if (currency == null) currency = "USD";
        }

        public BigDecimal marketValue() {
            return quantity.multiply(price);
        }

        public boolean hasZeroPrice() {
            return price == null || price.signum() == 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACCOUNT SNAPSHOT (from MSPM)
    // ═══════════════════════════════════════════════════════════════════════════
    public record AccountSnapshot(@NotNull Integer accountId, @NotNull Integer clientId, String clientName,
                                  @NotNull Integer fundId, String fundName, String baseCurrency, String accountNumber,
                                  @Valid List<Position> positions) {
        public int positionCount() {
            return positions != null ? positions.size() : 0;
        }

        public boolean isEmpty() {
            return positions == null || positions.isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD STATUS
    // ═══════════════════════════════════════════════════════════════════════════
    public record EodStatus(Integer accountId, LocalDate businessDate, String status,  // RUNNING, COMPLETED, FAILED
                            Integer positionCount, LocalDateTime startedAt, LocalDateTime completedAt,
                            String errorMessage) {
        public boolean isCompleted() {
            return "COMPLETED".equals(status);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KAFKA EVENTS
    // ═══════════════════════════════════════════════════════════════════════════
    public record PositionChangeEvent(String eventType, Integer accountId, Integer clientId, int positionCount,
                                      Instant timestamp) {
        public PositionChangeEvent(String eventType, Integer accountId, Integer clientId, int count) {
            this(eventType, accountId, clientId, count, Instant.now());
        }
    }
}
