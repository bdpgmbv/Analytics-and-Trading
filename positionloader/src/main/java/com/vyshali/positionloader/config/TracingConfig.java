package com.vyshali.positionloader.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing configuration for distributed tracing with OpenTelemetry.
 * 
 * Enabled via: loader.tracing.enabled=true
 * 
 * Configure OTLP exporter in application.yml:
 * management:
 *   tracing:
 *     sampling:
 *       probability: 0.1  # 10% sampling
 *   otlp:
 *     tracing:
 *       endpoint: http://otel-collector:4318/v1/traces
 */
@Configuration
@ConditionalOnProperty(name = "loader.tracing.enabled", havingValue = "true", matchIfMissing = false)
public class TracingConfig {

    /**
     * Enable @Observed annotation support for custom span creation.
     * 
     * Usage:
     * @Observed(name = "my-operation", contextualName = "process-account")
     * public void myMethod() { ... }
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
