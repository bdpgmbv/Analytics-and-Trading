package com.vyshali.common.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Helper for adding context to logs via MDC (Mapped Diagnostic Context).
 * 
 * Adds structured context that appears in all log lines within the scope.
 * Works with JSON logging to enable log aggregation and filtering.
 * 
 * Usage:
 * <pre>
 * // Simple usage
 * LogContext.with("accountId", 1001).run(() -> processAccount());
 * 
 * // Multiple context values
 * LogContext.with("accountId", 1001)
 *           .and("orderId", "ORD-123")
 *           .and("correlationId", UUID.randomUUID())
 *           .run(() -> {
 *               log.info("Processing order"); // includes all context
 *               processOrder();
 *           });
 * 
 * // With return value
 * Result result = LogContext.with("accountId", 1001)
 *                          .call(() -> fetchData());
 * 
 * // Async-safe - propagate context to other threads
 * LogContext.Snapshot snapshot = LogContext.capture();
 * executor.submit(() -> {
 *     LogContext.restore(snapshot).run(() -> doWork());
 * });
 * </pre>
 */
public final class LogContext {

    // Common MDC keys used across services
    public static final String ACCOUNT_ID = "accountId";
    public static final String CLIENT_ID = "clientId";
    public static final String ORDER_ID = "orderId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String BUSINESS_DATE = "businessDate";
    public static final String SOURCE = "source";

    private LogContext() {}

    /**
     * Start building a log context with a key-value pair.
     */
    public static Builder with(String key, Object value) {
        return new Builder().and(key, value);
    }

    /**
     * Start with account ID context.
     */
    public static Builder forAccount(int accountId) {
        return new Builder().and(ACCOUNT_ID, accountId);
    }

    /**
     * Start with order ID context.
     */
    public static Builder forOrder(String orderId) {
        return new Builder().and(ORDER_ID, orderId);
    }

    /**
     * Capture current MDC context for propagation to other threads.
     */
    public static Snapshot capture() {
        return new Snapshot(MDC.getCopyOfContextMap());
    }

    /**
     * Restore a captured snapshot.
     */
    public static Builder restore(Snapshot snapshot) {
        Builder builder = new Builder();
        if (snapshot.context != null) {
            builder.context.putAll(snapshot.context);
        }
        return builder;
    }

    /**
     * Clear all MDC context.
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Builder for composing log context.
     */
    public static class Builder {
        private final Map<String, String> context = new HashMap<>();

        /**
         * Add a context value.
         */
        public Builder and(String key, Object value) {
            if (key != null && value != null) {
                context.put(key, value.toString());
            }
            return this;
        }

        /**
         * Add account ID.
         */
        public Builder accountId(int accountId) {
            return and(ACCOUNT_ID, accountId);
        }

        /**
         * Add client ID.
         */
        public Builder clientId(int clientId) {
            return and(CLIENT_ID, clientId);
        }

        /**
         * Add order ID.
         */
        public Builder orderId(String orderId) {
            return and(ORDER_ID, orderId);
        }

        /**
         * Add correlation ID.
         */
        public Builder correlationId(String correlationId) {
            return and(CORRELATION_ID, correlationId);
        }

        /**
         * Add trace ID (for distributed tracing).
         */
        public Builder traceId(String traceId) {
            return and(TRACE_ID, traceId);
        }

        /**
         * Run a task with this context.
         */
        public void run(Runnable task) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                context.forEach(MDC::put);
                task.run();
            } finally {
                restorePrevious(previous);
            }
        }

        /**
         * Run a callable with this context and return result.
         */
        public <T> T call(Callable<T> task) throws Exception {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                context.forEach(MDC::put);
                return task.call();
            } finally {
                restorePrevious(previous);
            }
        }

        /**
         * Run a supplier with this context and return result.
         */
        public <T> T supply(Supplier<T> supplier) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                context.forEach(MDC::put);
                return supplier.get();
            } finally {
                restorePrevious(previous);
            }
        }

        /**
         * Run without checked exception handling.
         */
        public <T> T callUnchecked(Callable<T> task) {
            try {
                return call(task);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void restorePrevious(Map<String, String> previous) {
            context.keySet().forEach(MDC::remove);
            if (previous != null) {
                MDC.setContextMap(previous);
            }
        }
    }

    /**
     * Captured MDC context for thread propagation.
     */
    public record Snapshot(Map<String, String> context) {}
}
