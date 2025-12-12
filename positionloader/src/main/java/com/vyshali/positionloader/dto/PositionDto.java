package com.vyshali.positionloader.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Position Loader DTOs.
 * Keep your existing DTOs - this shows the expected structure.
 */
public final class PositionDto {

    private PositionDto() {}

    /**
     * Position update from external source.
     */
    public record PositionUpdate(
            int accountId,
            int productId,
            String ticker,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal marketValueLocal,
            BigDecimal marketValueBase,
            String currency,
            LocalDate businessDate,
            String source
    ) {}

    /**
     * Position snapshot.
     */
    public record PositionSnapshot(
            int accountId,
            int productId,
            String ticker,
            BigDecimal quantity,
            BigDecimal currentPrice,
            BigDecimal averageCost,
            BigDecimal marketValueLocal,
            BigDecimal marketValueBase,
            BigDecimal unrealizedPnl,
            String currency,
            String positionType,
            boolean isExcluded,
            LocalDateTime updatedAt
    ) {}

    /**
     * Batch information.
     */
    public record BatchInfo(
            long batchId,
            int accountId,
            String status,
            LocalDate businessDate,
            String source,
            LocalDateTime createdAt,
            LocalDateTime activatedAt
    ) {}

    /**
     * EOD run status.
     */
    public record EodStatus(
            LocalDate businessDate,
            String status,
            int accountsProcessed,
            int accountsFailed,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String errorMessage
    ) {}
}
