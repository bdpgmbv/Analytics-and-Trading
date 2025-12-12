package com.vyshali.common.exception;

/**
 * Exception thrown when trade execution fails.
 * Related to Issue #2: No tracking for executed trades.
 */
public class TradeExecutionException extends FxAnalyzerException {

    private final String executionRef;
    private final String tradeType;
    
    public TradeExecutionException(String executionRef, String message) {
        super("FXAN-3001", message);
        this.executionRef = executionRef;
        this.tradeType = null;
    }
    
    public TradeExecutionException(String executionRef, String tradeType, String message) {
        super("FXAN-3001", message);
        this.executionRef = executionRef;
        this.tradeType = tradeType;
    }
    
    public TradeExecutionException(String executionRef, String message, Throwable cause) {
        super("FXAN-3001", message, cause);
        this.executionRef = executionRef;
        this.tradeType = null;
    }
    
    public String getExecutionRef() {
        return executionRef;
    }
    
    public String getTradeType() {
        return tradeType;
    }
    
    // Factory methods
    public static TradeExecutionException rejected(String executionRef, String reason) {
        return new TradeExecutionException(executionRef, "Trade rejected: " + reason);
    }
    
    public static TradeExecutionException timeout(String executionRef) {
        return new TradeExecutionException(executionRef, "Trade execution timed out");
    }
    
    public static TradeExecutionException connectionFailed(String executionRef, Throwable cause) {
        return new TradeExecutionException(executionRef, "Failed to connect to FX Matrix", cause);
    }
}
