package com.vyshali.common.logging;

import org.slf4j.MDC;

import java.util.concurrent.Callable;

/**
 * Helper for adding context to logs (accountId, correlationId, etc.)
 * Usage: LogContext.with("accountId", "123").run(() -> processAccount());
 */
public final class LogContext {

    private LogContext() {}

    public static Builder with(String key, Object value) {
        return new Builder().and(key, value);
    }

    public static class Builder {
        private final java.util.Map<String, String> context = new java.util.HashMap<>();

        public Builder and(String key, Object value) {
            if (value != null) {
                context.put(key, value.toString());
            }
            return this;
        }

        public void run(Runnable task) {
            context.forEach(MDC::put);
            try {
                task.run();
            } finally {
                context.keySet().forEach(MDC::remove);
            }
        }

        public <T> T call(Callable<T> task) throws Exception {
            context.forEach(MDC::put);
            try {
                return task.call();
            } finally {
                context.keySet().forEach(MDC::remove);
            }
        }
    }
}
