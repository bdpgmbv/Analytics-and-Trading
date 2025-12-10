package com.vyshali.positionloader.exception;

/**
 * Exception for MSPM service failures.
 * Includes context about whether the error is retryable.
 */
public class MspmServiceException extends RuntimeException {

    private final Integer accountId;
    private final boolean retryable;

    public MspmServiceException(String message, Integer accountId, boolean retryable) {
        super(message);
        this.accountId = accountId;
        this.retryable = retryable;
    }

    public MspmServiceException(String message, Integer accountId, boolean retryable, Throwable cause) {
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