package com.vyshali.common.util;

import com.vyshali.common.exception.CommonExceptions.RetryExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Utility for retry operations with exponential backoff.
 * 
 * Usage:
 * <pre>
 * // Simple retry
 * String result = RetryHelper.retry(3, () -> fetchData());
 * 
 * // With exponential backoff
 * String result = RetryHelper.builder()
 *     .maxAttempts(5)
 *     .initialDelay(Duration.ofSeconds(1))
 *     .maxDelay(Duration.ofSeconds(30))
 *     .multiplier(2.0)
 *     .retryOn(IOException.class)
 *     .execute(() -> fetchData());
 * </pre>
 */
public final class RetryHelper {

    private static final Logger log = LoggerFactory.getLogger(RetryHelper.class);

    private RetryHelper() {}

    /**
     * Simple retry with default settings.
     */
    public static <T> T retry(int maxAttempts, Callable<T> action) throws RetryExhaustedException {
        return builder()
                .maxAttempts(maxAttempts)
                .execute(action);
    }

    /**
     * Retry with delay between attempts.
     */
    public static <T> T retry(int maxAttempts, Duration delay, Callable<T> action) 
            throws RetryExhaustedException {
        return builder()
                .maxAttempts(maxAttempts)
                .initialDelay(delay)
                .execute(action);
    }

    /**
     * Start building a retry configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for configuring retry behavior.
     */
    public static class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(60);
        private double multiplier = 2.0;
        private Predicate<Throwable> retryPredicate = t -> true;
        private String operationName = "operation";

        public Builder maxAttempts(int attempts) {
            this.maxAttempts = Math.max(1, attempts);
            return this;
        }

        public Builder initialDelay(Duration delay) {
            this.initialDelay = delay;
            return this;
        }

        public Builder maxDelay(Duration delay) {
            this.maxDelay = delay;
            return this;
        }

        public Builder multiplier(double mult) {
            this.multiplier = Math.max(1.0, mult);
            return this;
        }

        public Builder retryOn(Class<? extends Throwable>... exceptions) {
            this.retryPredicate = t -> {
                for (Class<? extends Throwable> ex : exceptions) {
                    if (ex.isInstance(t)) return true;
                }
                return false;
            };
            return this;
        }

        public Builder retryIf(Predicate<Throwable> predicate) {
            this.retryPredicate = predicate;
            return this;
        }

        public Builder named(String name) {
            this.operationName = name;
            return this;
        }

        /**
         * Execute the action with retry.
         */
        public <T> T execute(Callable<T> action) throws RetryExhaustedException {
            Exception lastException = null;
            long currentDelay = initialDelay.toMillis();

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return action.call();
                } catch (Exception e) {
                    lastException = e;

                    if (!retryPredicate.test(e)) {
                        log.debug("{} failed with non-retryable exception: {}", 
                                operationName, e.getMessage());
                        throw new RetryExhaustedException(
                                operationName + " failed: " + e.getMessage(), 1, e);
                    }

                    if (attempt == maxAttempts) {
                        log.error("{} failed after {} attempts: {}", 
                                operationName, maxAttempts, e.getMessage());
                        break;
                    }

                    log.warn("{} attempt {}/{} failed: {}. Retrying in {}ms", 
                            operationName, attempt, maxAttempts, e.getMessage(), currentDelay);

                    sleep(currentDelay);

                    // Exponential backoff
                    currentDelay = Math.min(
                            (long) (currentDelay * multiplier),
                            maxDelay.toMillis()
                    );
                }
            }

            throw new RetryExhaustedException(
                    operationName + " failed after " + maxAttempts + " attempts",
                    maxAttempts, lastException);
        }

        /**
         * Execute without returning a value.
         */
        public void executeVoid(Runnable action) throws RetryExhaustedException {
            execute(() -> {
                action.run();
                return null;
            });
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", e);
            }
        }
    }
}
