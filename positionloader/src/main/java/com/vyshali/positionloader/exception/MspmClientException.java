package com.vyshali.positionloader.exception;

/**
 * Exception for MSPM client errors.
 */
public class MspmClientException extends RuntimeException {
    
    public MspmClientException(String message) {
        super(message);
    }
    
    public MspmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
