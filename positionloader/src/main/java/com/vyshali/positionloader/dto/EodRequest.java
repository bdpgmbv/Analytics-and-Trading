package com.vyshali.positionloader.dto;

import java.time.LocalDate;

/**
 * EOD processing request.
 */
public record EodRequest(
    int accountId,
    LocalDate businessDate,
    String source,
    boolean forceReprocess,
    boolean skipValidation
) {
    
    /**
     * Create a simple EOD request for today.
     */
    public static EodRequest of(int accountId) {
        return new EodRequest(accountId, LocalDate.now(), "KAFKA", false, false);
    }
    
    /**
     * Create an EOD request for a specific date.
     */
    public static EodRequest of(int accountId, LocalDate businessDate) {
        return new EodRequest(accountId, businessDate, "KAFKA", false, false);
    }
    
    /**
     * Create an EOD request with source.
     */
    public static EodRequest of(int accountId, LocalDate businessDate, String source) {
        return new EodRequest(accountId, businessDate, source, false, false);
    }
    
    /**
     * Create a forced reprocess request.
     */
    public static EodRequest forceReprocess(int accountId, LocalDate businessDate) {
        return new EodRequest(accountId, businessDate, "REPROCESS", true, false);
    }
    
    /**
     * Create a request that skips validation.
     */
    public EodRequest withSkipValidation() {
        return new EodRequest(accountId, businessDate, source, forceReprocess, true);
    }
    
    /**
     * Create a request with force reprocess.
     */
    public EodRequest withForceReprocess() {
        return new EodRequest(accountId, businessDate, source, true, skipValidation);
    }
}
