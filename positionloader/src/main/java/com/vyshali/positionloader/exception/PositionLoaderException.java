package com.vyshali.positionloader.exception;

/*
 * 12/10/2025 - 11:49 AM
 * @author Vyshali Prabananth Lal
 */

public class PositionLoaderException extends RuntimeException {

    private final ErrorCode code;

    public enum ErrorCode {
        MSPM_UNAVAILABLE(101, true), MSPM_TIMEOUT(102, true), VALIDATION_FAILED(201, false), ZERO_PRICE_DETECTED(202, false), DB_ERROR(301, true), TIMEOUT(401, true);

        private final int number;
        private final boolean retryable;

        ErrorCode(int number, boolean retryable) {
            this.number = number;
            this.retryable = retryable;
        }

        public int getNumber() {
            return number;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    public PositionLoaderException(ErrorCode code, String message) {
        super(String.format("[%s-%d] %s", code.name(), code.getNumber(), message));
        this.code = code;
    }

    public PositionLoaderException(ErrorCode code, String message, Throwable cause) {
        super(String.format("[%s-%d] %s", code.name(), code.getNumber(), message), cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return code.isRetryable();
    }

    // Factory methods
    public static PositionLoaderException mspmUnavailable(String msg) {
        return new PositionLoaderException(ErrorCode.MSPM_UNAVAILABLE, msg);
    }

    public static PositionLoaderException zeroPriceDetected(Integer accountId, int count) {
        return new PositionLoaderException(ErrorCode.ZERO_PRICE_DETECTED, String.format("Account %d has %d zero-price positions", accountId, count));
    }

    public static PositionLoaderException validationFailed(String reason) {
        return new PositionLoaderException(ErrorCode.VALIDATION_FAILED, reason);
    }
}