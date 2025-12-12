package com.vyshali.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Shared Data Transfer Objects used across services.
 */
public final class SharedDto {

    private SharedDto() {}

    /**
     * Account information.
     */
    public record AccountDTO(
            int accountId,
            int clientId,
            String accountName,
            String accountNumber,
            String baseCurrency,
            String status
    ) {
        public boolean isActive() {
            return "ACTIVE".equalsIgnoreCase(status) || status == null;
        }
    }

    /**
     * Product/instrument information.
     */
    public record ProductDTO(
            int productId,
            String ticker,
            String assetClass,
            String currency,
            String status
    ) {
        public boolean isActive() {
            return !"INACTIVE".equalsIgnoreCase(status);
        }
    }

    /**
     * FX rate data.
     */
    public record FxRateDTO(
            String currencyPair,
            BigDecimal rate,
            BigDecimal bid,
            BigDecimal ask,
            LocalDateTime timestamp,
            String source
    ) {
        public static FxRateDTO of(String pair, BigDecimal rate) {
            return new FxRateDTO(pair, rate, rate, rate, LocalDateTime.now(), "SYSTEM");
        }

        public String baseCurrency() {
            return currencyPair != null && currencyPair.length() >= 3 
                    ? currencyPair.substring(0, 3) : null;
        }

        public String quoteCurrency() {
            return currencyPair != null && currencyPair.length() >= 6 
                    ? currencyPair.substring(3, 6) : null;
        }
    }

    /**
     * Audit log event.
     */
    public record AuditEventDTO(
            Long auditId,
            String eventType,
            String entityId,
            String actor,
            String payload,
            LocalDateTime createdAt
    ) {
        public static AuditEventDTO create(String eventType, String entityId, String payload) {
            return new AuditEventDTO(null, eventType, entityId, "SYSTEM", payload, LocalDateTime.now());
        }

        public static AuditEventDTO create(String eventType, String entityId, String actor, String payload) {
            return new AuditEventDTO(null, eventType, entityId, actor, payload, LocalDateTime.now());
        }
    }

    /**
     * Holiday calendar entry.
     */
    public record HolidayDTO(
            LocalDate date,
            String name,
            String country,
            boolean isHalfDay
    ) {
        public static HolidayDTO of(LocalDate date, String name, String country) {
            return new HolidayDTO(date, name, country, false);
        }
    }

    /**
     * DLQ message.
     */
    public record DlqMessageDTO(
            Long id,
            String topic,
            String messageKey,
            String payload,
            String errorMessage,
            String status,
            int retryCount,
            LocalDateTime createdAt,
            LocalDateTime nextRetryAt
    ) {}

    /**
     * Simple position snapshot (for cross-service use).
     */
    public record PositionSnapshotDTO(
            int accountId,
            int productId,
            BigDecimal quantity,
            BigDecimal price,
            String currency,
            BigDecimal marketValue,
            LocalDate businessDate,
            String source
    ) {
        public static PositionSnapshotDTO of(int accountId, int productId, 
                BigDecimal quantity, BigDecimal price, String currency, LocalDate date) {
            BigDecimal mv = quantity != null && price != null 
                    ? quantity.multiply(price) : BigDecimal.ZERO;
            return new PositionSnapshotDTO(accountId, productId, quantity, price, 
                    currency, mv, date, "SYSTEM");
        }
    }

    /**
     * Result wrapper for operations.
     */
    public record OperationResult<T>(
            boolean success,
            T data,
            String errorMessage,
            String errorCode
    ) {
        public static <T> OperationResult<T> success(T data) {
            return new OperationResult<>(true, data, null, null);
        }

        public static <T> OperationResult<T> failure(String message) {
            return new OperationResult<>(false, null, message, null);
        }

        public static <T> OperationResult<T> failure(String message, String code) {
            return new OperationResult<>(false, null, message, code);
        }
    }
}
