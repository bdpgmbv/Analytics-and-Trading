package com.vyshali.positionloader.exception;

import java.time.LocalDate;

/**
 * Exception thrown when EOD processing fails.
 */
public class EodProcessingException extends RuntimeException {
    
    private final Integer accountId;
    private final LocalDate businessDate;
    private final boolean retryable;
    
    public EodProcessingException(String message) {
        super(message);
        this.accountId = null;
        this.businessDate = null;
        this.retryable = false;
    }
    
    public EodProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.accountId = null;
        this.businessDate = null;
        this.retryable = isRetryableCause(cause);
    }
    
    public EodProcessingException(String message, Integer accountId, LocalDate businessDate) {
        super(message);
        this.accountId = accountId;
        this.businessDate = businessDate;
        this.retryable = false;
    }
    
    public EodProcessingException(String message, Integer accountId, LocalDate businessDate, 
            boolean retryable) {
        super(message);
        this.accountId = accountId;
        this.businessDate = businessDate;
        this.retryable = retryable;
    }
    
    public EodProcessingException(String message, Integer accountId, LocalDate businessDate, 
            Throwable cause) {
        super(message, cause);
        this.accountId = accountId;
        this.businessDate = businessDate;
        this.retryable = isRetryableCause(cause);
    }
    
    public Integer getAccountId() {
        return accountId;
    }
    
    public LocalDate getBusinessDate() {
        return businessDate;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * Determine if the cause is retryable.
     */
    private boolean isRetryableCause(Throwable cause) {
        if (cause == null) return false;
        
        // Network/connection errors are typically retryable
        String className = cause.getClass().getName();
        return className.contains("Timeout") ||
               className.contains("Connection") ||
               className.contains("Network") ||
               cause instanceof java.net.SocketTimeoutException ||
               cause instanceof java.io.IOException;
    }
}
