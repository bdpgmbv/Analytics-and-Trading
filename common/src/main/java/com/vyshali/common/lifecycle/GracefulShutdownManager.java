package com.vyshali.common.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates graceful shutdown across all services.
 * 
 * Usage:
 * 1. Register cleanup tasks that must complete before shutdown
 * 2. Check isShuttingDown() before starting new work
 * 3. Track active jobs to ensure they complete
 * 
 * Example:
 * <pre>
 * shutdownManager.registerTask("kafka-consumer", () -> consumer.close());
 * shutdownManager.registerTask("redis-connection", () -> redis.close());
 * 
 * if (shutdownManager.isShuttingDown()) {
 *     return; // Don't start new work
 * }
 * 
 * shutdownManager.jobStarted();
 * try {
 *     // do work
 * } finally {
 *     shutdownManager.jobEnded();
 * }
 * </pre>
 */
@Component
public class GracefulShutdownManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final List<ShutdownTask> tasks = new ArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    /**
     * Register a task to run during shutdown.
     */
    public void registerTask(String name, Runnable task) {
        registerTask(name, task, 0);
    }

    /**
     * Register a task with priority (lower = runs first).
     */
    public void registerTask(String name, Runnable task, int priority) {
        synchronized (tasks) {
            tasks.add(new ShutdownTask(name, task, priority));
            tasks.sort((a, b) -> Integer.compare(a.priority, b.priority));
        }
        log.info("Registered shutdown task: {} (priority={})", name, priority);
    }

    /**
     * Check if shutdown is in progress.
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Mark a job as started.
     */
    public void jobStarted() {
        activeJobs.incrementAndGet();
    }

    /**
     * Mark a job as ended.
     */
    public void jobEnded() {
        activeJobs.decrementAndGet();
    }

    /**
     * Get active job count.
     */
    public int getActiveJobCount() {
        return activeJobs.get();
    }

    /**
     * Set shutdown timeout.
     */
    public void setTimeoutSeconds(int seconds) {
        this.timeoutSeconds = seconds;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.warn("Shutdown initiated. Active jobs: {}", activeJobs.get());
        shuttingDown.set(true);

        // Phase 1: Wait for active jobs to complete
        waitForActiveJobs();

        // Phase 2: Run shutdown tasks
        runShutdownTasks();

        log.info("Graceful shutdown complete");
    }

    private void waitForActiveJobs() {
        int waited = 0;
        int maxWait = timeoutSeconds - 5; // Reserve 5s for tasks

        while (activeJobs.get() > 0 && waited < maxWait) {
            log.info("Waiting for {} active jobs... ({}s)", activeJobs.get(), waited);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Shutdown wait interrupted");
                break;
            }
            waited++;
        }

        if (activeJobs.get() > 0) {
            log.error("Timeout waiting for {} jobs to complete!", activeJobs.get());
        }
    }

    private void runShutdownTasks() {
        List<ShutdownTask> tasksCopy;
        synchronized (tasks) {
            tasksCopy = new ArrayList<>(tasks);
        }

        if (tasksCopy.isEmpty()) {
            return;
        }

        log.info("Running {} shutdown tasks...", tasksCopy.size());
        CountDownLatch latch = new CountDownLatch(tasksCopy.size());

        for (ShutdownTask task : tasksCopy) {
            Thread.ofVirtual().name("shutdown-" + task.name).start(() -> {
                try {
                    log.debug("Running shutdown task: {}", task.name);
                    task.runnable.run();
                    log.info("Completed shutdown task: {}", task.name);
                } catch (Exception e) {
                    log.error("Shutdown task '{}' failed: {}", task.name, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Some shutdown tasks did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Shutdown tasks interrupted");
        }
    }

    private record ShutdownTask(String name, Runnable runnable, int priority) {}
}
