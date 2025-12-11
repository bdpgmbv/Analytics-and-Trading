package com.vyshali.positionloader.exception;

/*
 * 12/11/2025 - 11:44 AM
 * @author Vyshali Prabananth Lal
 */

public class PositionLoaderException extends RuntimeException {

    private final Integer accountId;
    private final boolean retryable;

    public PositionLoaderException(String message) {
        this(message, null, false);
    }

    public PositionLoaderException(String message, Integer accountId, boolean retryable) {
        super(message);
        this.accountId = accountId;
        this.retryable = retryable;
    }

    public PositionLoaderException(String message, Integer accountId, boolean retryable, Throwable cause) {
        super(message, cause);
        this.accountId = accountId;
        this.retryable = retryable;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public boolean isRetryable() {
        return retryable;
    }
}