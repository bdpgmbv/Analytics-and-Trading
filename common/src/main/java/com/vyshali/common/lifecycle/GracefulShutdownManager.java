package com.vyshali.common.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates graceful shutdown across all services.
 * Register cleanup tasks that must complete before shutdown.
 */
@Slf4j
@Component
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

    private final List<ShutdownTask> tasks = new ArrayList<>();
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    public void registerTask(String name, Runnable task) {
        tasks.add(new ShutdownTask(name, task));
        log.info("Registered shutdown task: {}", name);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Initiating graceful shutdown with {} tasks...", tasks.size());
        
        CountDownLatch latch = new CountDownLatch(tasks.size());
        
        for (ShutdownTask task : tasks) {
            Thread.ofVirtual().start(() -> {
                try {
                    log.info("Running shutdown task: {}", task.name);
                    task.runnable.run();
                    log.info("Completed shutdown task: {}", task.name);
                } catch (Exception e) {
                    log.error("Shutdown task failed: {}", task.name, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            boolean completed = latch.await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Shutdown timed out - some tasks did not complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Shutdown interrupted");
        }
        
        log.info("Graceful shutdown complete");
    }

    private record ShutdownTask(String name, Runnable runnable) {}
}
