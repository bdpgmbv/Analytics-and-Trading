package com.vyshali.positionloader.dto;

import java.time.Instant;
import java.util.List;

/**
 * Result of a position upload operation.
 */
public record UploadResult(
    boolean success,
    int batchId,
    int positionCount,
    List<String> errors,
    List<String> warnings,
    Instant timestamp
) {
    
    /**
     * Compact constructor with defaults.
     */
    public UploadResult {
        if (errors == null) errors = List.of();
        if (warnings == null) warnings = List.of();
        if (timestamp == null) timestamp = Instant.now();
    }
    
    /**
     * Create a successful result.
     */
    public static UploadResult success(int batchId, int positionCount) {
        return new UploadResult(
            true,
            batchId,
            positionCount,
            List.of(),
            List.of(),
            Instant.now()
        );
    }
    
    /**
     * Create a successful result with warnings.
     */
    public static UploadResult successWithWarnings(int batchId, int positionCount, 
            List<String> warnings) {
        return new UploadResult(
            true,
            batchId,
            positionCount,
            List.of(),
            warnings,
            Instant.now()
        );
    }
    
    /**
     * Create a failure result.
     */
    public static UploadResult failure(List<String> errors) {
        return new UploadResult(
            false,
            -1,
            0,
            errors,
            List.of(),
            Instant.now()
        );
    }
    
    /**
     * Create a failure result with single error.
     */
    public static UploadResult failure(String error) {
        return failure(List.of(error));
    }
    
    /**
     * Check if there are any warnings.
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * Check if there are any errors.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
