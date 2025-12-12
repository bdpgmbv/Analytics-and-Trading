package com.vyshali.hedgeservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tab 6: Position Upload - Manual position uploads and validation.
 */
public class PositionUploadDto {
    
    /**
     * Request to upload positions.
     */
    public record UploadRequest(
        @NotNull Integer portfolioId,
        @NotNull LocalDate asOfDate,
        String source, // MANUAL, FILE_IMPORT, API
        java.util.List<PositionRow> positions
    ) {}
    
    /**
     * Individual position row in upload.
     */
    public record PositionRow(
        @NotNull String ticker,
        @NotNull String currency,
        @NotNull BigDecimal quantity,
        BigDecimal price,
        String account,
        String assetClass,
        String securityDescription,
        BigDecimal costBasis,
        String externalRef
    ) {}
    
    /**
     * Response after upload validation and processing.
     */
    public record UploadResponse(
        String uploadId,
        Integer portfolioId,
        LocalDate asOfDate,
        UploadStatus status,
        
        // Validation results
        int totalRows,
        int validRows,
        int invalidRows,
        java.util.List<ValidationError> validationErrors,
        
        // Processing results
        int positionsCreated,
        int positionsUpdated,
        int positionsSkipped,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime uploadedAt,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime processedAt,
        
        String uploadedBy
    ) {}
    
    public record ValidationError(
        int rowNumber,
        String field,
        String errorCode,
        String errorMessage,
        PositionRow invalidRow
    ) {}
    
    public enum UploadStatus {
        VALIDATING,
        VALIDATION_FAILED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    /**
     * Upload history for a portfolio.
     */
    public record UploadHistory(
        Integer portfolioId,
        java.util.List<UploadSummary> uploads,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime retrievedAt
    ) {}
    
    public record UploadSummary(
        String uploadId,
        LocalDate asOfDate,
        String source,
        UploadStatus status,
        int totalRows,
        int validRows,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime uploadedAt,
        
        String uploadedBy
    ) {}
}
