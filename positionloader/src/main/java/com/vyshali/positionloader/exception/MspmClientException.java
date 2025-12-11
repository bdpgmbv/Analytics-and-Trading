package com.vyshali.positionloader.exception;

/**
 * Exception thrown when MSPM API calls fail.
 */
public class MspmClientException extends RuntimeException {
    
    private final Integer statusCode;
    private final boolean retryable;
    
    public MspmClientException(String message) {
        super(message);
        this.statusCode = null;
        this.retryable = false;
    }
    
    public MspmClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.retryable = isRetryableCause(cause);
    }
    
    public MspmClientException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = isRetryableStatusCode(statusCode);
    }
    
    public MspmClientException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = isRetryableStatusCode(statusCode) || isRetryableCause(cause);
    }
    
    public Integer getStatusCode() {
        return statusCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * Check if HTTP status code is retryable.
     */
    private boolean isRetryableStatusCode(Integer code) {
        if (code == null) return false;
        
        // 5xx errors and 429 (Too Many Requests) are retryable
        return code >= 500 || code == 429 || code == 408; // Request Timeout
    }
    
    /**
     * Check if the cause is retryable.
     */
    private boolean isRetryableCause(Throwable cause) {
        if (cause == null) return false;
        
        String className = cause.getClass().getName();
        return className.contains("Timeout") ||
               className.contains("Connection") ||
               className.contains("Network") ||
               cause instanceof java.net.SocketTimeoutException ||
               cause instanceof java.net.ConnectException;
    }
}
