package com.vyshali.positionloader.config;

/*
 * SIMPLIFIED: Removed custom SpanCreator and SpanScope classes
 *
 * DELETED:
 * - SpanCreator class (25 lines) - Micrometer @Observed does this
 * - SpanScope class (20 lines) - Micrometer handles this automatically
 * - spanCreator() bean - no longer needed
 *
 * HOW TO USE TRACING NOW:
 *
 * Just add @Observed annotation to any method:
 *
 *   @Observed(name = "eod.processing")
 *   public void processEod(Integer accountId) {
 *       // Tracing happens automatically!
 *   }
 *
 * That's it! No manual span creation needed.
 */

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TracingConfig {

    @Value("${spring.application.name:positionloader}")
    private String applicationName;

    @Value("${management.tracing.sampling.probability:1.0}")
    private double samplingProbability;

    /**
     * Enable @Observed annotation for automatic span creation.
     * <p>
     * Usage in service classes:
     *
     * @Observed(name = "eod.processing", contextualName = "process-eod-account")
     * public void processEod(Integer accountId) { ... }
     * @Observed(name = "mspm.fetch")
     * public AccountSnapshotDTO fetchSnapshot(Integer accountId) { ... }
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        log.info("Tracing enabled: app={}, sampling={}%", applicationName, samplingProbability * 100);
        return new ObservedAspect(registry);
    }

    // DELETED: spanCreator() bean
    // DELETED: SpanCreator class
    // DELETED: SpanScope class
}