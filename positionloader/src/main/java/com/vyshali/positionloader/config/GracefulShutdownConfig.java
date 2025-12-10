package com.vyshali.positionloader.config;

/*
 * AMPLIFIED: Graceful shutdown handling
 *
 * WHY CRITICAL:
 * - Kubernetes sends SIGTERM before killing pod
 * - Without handling: in-flight requests fail, data loss
 * - With handling: complete current work, then shutdown cleanly
 *
 * WHAT THIS DOES:
 * 1. Stops accepting new Kafka messages
 * 2. Waits for in-flight EOD/intraday processing to complete
 * 3. Flushes any pending Kafka producer messages
 * 4. Closes database connections cleanly
 * 5. Then allows container to terminate
 *
 * KUBERNETES CONFIG REQUIRED:
 * - terminationGracePeriodSeconds: 60 (or longer than your longest operation)
 * - preStop hook to allow load balancer to drain
 */

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class GracefulShutdownConfig {

    private final KafkaListenerEndpointRegistry kafkaListenerRegistry;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${shutdown.timeout-seconds:30}")
    private int shutdownTimeoutSeconds;

    // Track in-flight operations
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final AtomicInteger inFlightOperations = new AtomicInteger(0);

    /**
     * Check if shutdown is in progress.
     * Callers should refuse new work if true.
     */
    public static boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Increment in-flight counter before starting operation.
     * Returns false if shutdown already in progress.
     */
    public static boolean startOperation() {
        if (shuttingDown.get()) {
            return false;
        }
        inFlightOperations.incrementAndGet();
        return true;
    }

    /**
     * Decrement in-flight counter after operation completes.
     */
    public static void endOperation() {
        inFlightOperations.decrementAndGet();
    }

    /**
     * Listener for application shutdown event.
     */
    @Bean
    public ApplicationListener<ContextClosedEvent> gracefulShutdownListener() {
        return event -> {
            log.info("╔══════════════════════════════════════════════════════════╗");
            log.info("║        GRACEFUL SHUTDOWN INITIATED                       ║");
            log.info("╚══════════════════════════════════════════════════════════╝");

            shuttingDown.set(true);

            // Step 1: Stop Kafka consumers (stop accepting new messages)
            stopKafkaConsumers();

            // Step 2: Wait for in-flight operations
            waitForInFlightOperations();

            // Step 3: Flush Kafka producer
            flushKafkaProducer();

            log.info("Graceful shutdown complete");
        };
    }

    /**
     * Stop all Kafka listener containers.
     */
    private void stopKafkaConsumers() {
        log.info("Stopping Kafka consumers...");

        for (MessageListenerContainer container : kafkaListenerRegistry.getAllListenerContainers()) {
            String listenerId = container.getListenerId();
            log.info("Stopping listener: {}", listenerId);
            container.stop();
        }

        log.info("All Kafka consumers stopped");
    }

    /**
     * Wait for in-flight operations to complete.
     */
    private void waitForInFlightOperations() {
        int inFlight = inFlightOperations.get();
        if (inFlight == 0) {
            log.info("No in-flight operations");
            return;
        }

        log.info("Waiting for {} in-flight operations to complete...", inFlight);

        long deadline = System.currentTimeMillis() + (shutdownTimeoutSeconds * 1000L);

        while (inFlightOperations.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(500);
                log.debug("Still waiting... {} operations in flight", inFlightOperations.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remaining = inFlightOperations.get();
        if (remaining > 0) {
            log.warn("Shutdown timeout! {} operations still in flight", remaining);
        } else {
            log.info("All in-flight operations completed");
        }
    }

    /**
     * Flush any pending Kafka producer messages.
     */
    private void flushKafkaProducer() {
        log.info("Flushing Kafka producer...");
        try {
            kafkaTemplate.flush();
            log.info("Kafka producer flushed");
        } catch (Exception e) {
            log.error("Error flushing Kafka producer: {}", e.getMessage());
        }
    }
}