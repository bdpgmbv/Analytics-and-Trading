package com.vyshali.common.exception;

/**
 * Base exception for all FX Analyzer application exceptions
 */
public class FxAnalyzerException extends RuntimeException {

    private final String errorCode;
    
    public FxAnalyzerException(String message) {
        super(message);
        this.errorCode = "FXAN-0000";
    }
    
    public FxAnalyzerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public FxAnalyzerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
