package com.vyshali.positionloader.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Objects for the Position Loader API.
 */
public class Dto {
    
    private Dto() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Position data for API responses.
     */
    public record Position(
        Long positionId,
        int accountId,
        int productId,
        LocalDate businessDate,
        BigDecimal quantity,
        BigDecimal price,
        String currency,
        BigDecimal marketValueLocal,
        BigDecimal marketValueBase,
        String source,
        String positionType
    ) {}
    
    /**
     * EOD status for API responses.
     */
    public record EodStatus(
        int accountId,
        LocalDate businessDate,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int positionCount,
        String errorMessage
    ) {}
    
    /**
     * Batch information for API responses.
     */
    public record BatchInfo(
        int accountId,
        int batchId,
        LocalDate businessDate,
        String status,
        int positionCount,
        LocalDateTime createdAt,
        LocalDateTime activatedAt,
        LocalDateTime archivedAt,
        String errorMessage
    ) {}
    
    /**
     * Account summary for API responses.
     */
    public record AccountSummary(
        int accountId,
        String accountName,
        String baseCurrency,
        int positionCount,
        BigDecimal totalMarketValue,
        LocalDate lastEodDate,
        String lastEodStatus
    ) {}
    
    /**
     * Reconciliation result for API responses.
     */
    public record ReconciliationResult(
        int accountId,
        LocalDate businessDate,
        String status,
        int positionCount,
        int mismatches,
        BigDecimal totalDifference,
        String details
    ) {}
    
    /**
     * DLQ message for API responses.
     */
    public record DlqMessage(
        long id,
        String topic,
        String messageKey,
        String payload,
        String errorMessage,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime lastRetryAt
    ) {}
    
    /**
     * Holiday for API responses.
     */
    public record Holiday(
        LocalDate date,
        String name,
        String country,
        boolean isHalfDay
    ) {}
    
    /**
     * Feature flags for API responses.
     */
    public record FeatureFlags(
        boolean eodProcessingEnabled,
        boolean intradayProcessingEnabled,
        boolean validationEnabled,
        boolean duplicateDetectionEnabled,
        boolean reconciliationEnabled,
        boolean archivalEnabled
    ) {}
}
