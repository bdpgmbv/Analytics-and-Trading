package com.vyshali.common.exception;

/**
 * Common exceptions used across services.
 */
public final class CommonExceptions {

    private CommonExceptions() {}

    /**
     * Base service exception with error code support.
     */
    public static class ServiceException extends RuntimeException {
        private final String errorCode;
        private final boolean retryable;

        public ServiceException(String message) {
            this(message, null, null, false);
        }

        public ServiceException(String message, Throwable cause) {
            this(message, null, cause, false);
        }

        public ServiceException(String message, String errorCode) {
            this(message, errorCode, null, false);
        }

        public ServiceException(String message, String errorCode, Throwable cause, boolean retryable) {
            super(message, cause);
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /**
     * Validation failed exception.
     */
    public static class ValidationException extends ServiceException {
        private final java.util.List<String> errors;

        public ValidationException(String message) {
            this(message, java.util.List.of(message));
        }

        public ValidationException(String message, java.util.List<String> errors) {
            super(message, "VALIDATION_ERROR");
            this.errors = errors != null ? errors : java.util.List.of();
        }

        public java.util.List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Retry exhausted exception.
     */
    public static class RetryExhaustedException extends ServiceException {
        private final int attemptsMade;

        public RetryExhaustedException(String message, int attempts) {
            super(message, "RETRY_EXHAUSTED", null, false);
            this.attemptsMade = attempts;
        }

        public RetryExhaustedException(String message, int attempts, Throwable cause) {
            super(message, "RETRY_EXHAUSTED", cause, false);
            this.attemptsMade = attempts;
        }

        public int getAttemptsMade() {
            return attemptsMade;
        }
    }

    /**
     * Resource not found exception.
     */
    public static class NotFoundException extends ServiceException {
        private final String resourceType;
        private final String resourceId;

        public NotFoundException(String resourceType, String resourceId) {
            super(String.format("%s not found: %s", resourceType, resourceId), "NOT_FOUND");
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        public String getResourceType() {
            return resourceType;
        }

        public String getResourceId() {
            return resourceId;
        }
    }

    /**
     * External service unavailable.
     */
    public static class ServiceUnavailableException extends ServiceException {
        private final String serviceName;

        public ServiceUnavailableException(String serviceName) {
            super(serviceName + " is unavailable", "SERVICE_UNAVAILABLE", null, true);
            this.serviceName = serviceName;
        }

        public ServiceUnavailableException(String serviceName, Throwable cause) {
            super(serviceName + " is unavailable: " + cause.getMessage(), 
                  "SERVICE_UNAVAILABLE", cause, true);
            this.serviceName = serviceName;
        }

        public String getServiceName() {
            return serviceName;
        }
    }

    /**
     * Circuit breaker open exception.
     */
    public static class CircuitBreakerOpenException extends ServiceException {
        private final String circuitBreakerName;

        public CircuitBreakerOpenException(String name) {
            super("Circuit breaker '" + name + "' is open", "CIRCUIT_OPEN", null, true);
            this.circuitBreakerName = name;
        }

        public String getCircuitBreakerName() {
            return circuitBreakerName;
        }
    }

    /**
     * Rate limit exceeded exception.
     */
    public static class RateLimitExceededException extends ServiceException {
        private final int retryAfterSeconds;

        public RateLimitExceededException() {
            this(60);
        }

        public RateLimitExceededException(int retryAfterSeconds) {
            super("Rate limit exceeded", "RATE_LIMITED", null, true);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * Configuration error exception.
     */
    public static class ConfigurationException extends ServiceException {
        public ConfigurationException(String message) {
            super(message, "CONFIG_ERROR");
        }
    }

    /**
     * Business rule violation.
     */
    public static class BusinessRuleException extends ServiceException {
        private final String ruleCode;

        public BusinessRuleException(String message) {
            this(message, null);
        }

        public BusinessRuleException(String message, String ruleCode) {
            super(message, "BUSINESS_RULE_VIOLATION");
            this.ruleCode = ruleCode;
        }

        public String getRuleCode() {
            return ruleCode;
        }
    }
}
