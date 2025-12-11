package com.vyshali.positionloader.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * EOD Processing Request.
 * Used to trigger end-of-day position processing.
 */
public record EodRequest(
    @NotNull(message = "Account ID is required")
    Integer accountId,
    
    @NotNull(message = "Business date is required")
    LocalDate businessDate,
    
    String source,
    boolean forceReprocess,
    boolean lateArrival
) {
    
    /**
     * Compact constructor with defaults.
     */
    public EodRequest {
        if (businessDate == null) businessDate = LocalDate.now();
        if (source == null) source = "KAFKA";
    }
    
    /**
     * Simple constructor for most common use case.
     */
    public static EodRequest of(Integer accountId) {
        return new EodRequest(accountId, LocalDate.now(), "KAFKA", false, false);
    }
    
    /**
     * Constructor with specific date.
     */
    public static EodRequest of(Integer accountId, LocalDate businessDate) {
        return new EodRequest(accountId, businessDate, "KAFKA", false, false);
    }
    
    /**
     * Create a late EOD request.
     */
    public static EodRequest lateEod(Integer accountId, LocalDate businessDate) {
        return new EodRequest(accountId, businessDate, "MANUAL", false, true);
    }
    
    /**
     * Create a force reprocess request.
     */
    public static EodRequest forceReprocess(Integer accountId, LocalDate businessDate) {
        return new EodRequest(accountId, businessDate, "MANUAL", true, false);
    }
}
