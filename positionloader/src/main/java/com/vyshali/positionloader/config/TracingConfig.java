package com.vyshali.positionloader.config;

/*
 * 12/10/2025 - NEW: Distributed tracing configuration
 *
 * PURPOSE:
 * Enable end-to-end tracing across services:
 * MSPM → Position Loader → Kafka → Price Service → WebSocket
 *
 * FEATURES:
 * - Trace ID propagation across HTTP and Kafka
 * - Span creation for key operations
 * - Integration with Tempo/Zipkin
 *
 * @author Vyshali Prabananth Lal
 */

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@Import(BraveAutoConfiguration.class)
public class TracingConfig {

    @Value("${spring.application.name:positionloader}")
    private String applicationName;

    @Value("${management.tracing.sampling.probability:1.0}")
    private double samplingProbability;

    /**
     * Enable @Observed annotation for custom spans.
     * <p>
     * Usage in service classes:
     * <pre>
     * @Observed(name = "eod.processing", contextualName = "process-eod-account")
     * public void processEod(Integer accountId) { ... }
     * </pre>
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        log.info("Tracing enabled: app={}, sampling={}%", applicationName, samplingProbability * 100);
        return new ObservedAspect(registry);
    }

    /**
     * Custom span creator for manual instrumentation.
     * <p>
     * Usage:
     * <pre>
     * try (var span = spanCreator.startSpan("mspm-fetch")) {
     *     // ... operation
     * }
     * </pre>
     */
    @Bean
    public SpanCreator spanCreator(Tracer tracer) {
        return new SpanCreator(tracer);
    }

    /**
     * Helper class for creating custom spans.
     */
    public static class SpanCreator {
        private final Tracer tracer;

        public SpanCreator(Tracer tracer) {
            this.tracer = tracer;
        }

        /**
         * Start a new span with the given name.
         */
        public SpanScope startSpan(String name) {
            var span = tracer.nextSpan().name(name).start();
            return new SpanScope(span, tracer.withSpan(span));
        }

        /**
         * Start a span with tags.
         */
        public SpanScope startSpan(String name, String... tags) {
            var spanBuilder = tracer.nextSpan().name(name);
            for (int i = 0; i < tags.length - 1; i += 2) {
                spanBuilder.tag(tags[i], tags[i + 1]);
            }
            var span = spanBuilder.start();
            return new SpanScope(span, tracer.withSpan(span));
        }

        /**
         * Get current trace ID (for logging).
         */
        public String getCurrentTraceId() {
            var span = tracer.currentSpan();
            return span != null ? span.context().traceId() : "no-trace";
        }
    }

    /**
     * Auto-closeable span scope.
     */
    public static class SpanScope implements AutoCloseable {
        private final io.micrometer.tracing.Span span;
        private final io.micrometer.tracing.Tracer.SpanInScope scope;

        SpanScope(io.micrometer.tracing.Span span, io.micrometer.tracing.Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        public void tag(String key, String value) {
            span.tag(key, value);
        }

        public void event(String message) {
            span.event(message);
        }

        public void error(Throwable t) {
            span.error(t);
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}