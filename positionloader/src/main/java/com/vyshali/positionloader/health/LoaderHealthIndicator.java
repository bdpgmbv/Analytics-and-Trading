package com.vyshali.positionloader.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Health indicator for the Position Loader service.
 * Tracks active jobs and provides health status.
 */
@Component
public class LoaderHealthIndicator implements HealthIndicator {
    
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final AtomicInteger totalJobsProcessed = new AtomicInteger(0);
    private final AtomicInteger failedJobs = new AtomicInteger(0);
    
    @Override
    public Health health() {
        int active = activeJobs.get();
        int total = totalJobsProcessed.get();
        int failed = failedJobs.get();
        
        Health.Builder builder;
        
        if (active > 100) {
            builder = Health.down()
                .withDetail("reason", "Too many active jobs");
        } else if (failed > 0 && total > 0 && (double) failed / total > 0.5) {
            builder = Health.down()
                .withDetail("reason", "High failure rate");
        } else if (active > 50) {
            builder = Health.status("DEGRADED")
                .withDetail("reason", "High job count");
        } else {
            builder = Health.up();
        }
        
        return builder
            .withDetail("activeJobs", active)
            .withDetail("totalProcessed", total)
            .withDetail("failed", failed)
            .build();
    }
    
    /**
     * Record job started.
     */
    public void jobStarted() {
        activeJobs.incrementAndGet();
    }
    
    /**
     * Record job ended successfully.
     */
    public void jobEnded() {
        activeJobs.decrementAndGet();
        totalJobsProcessed.incrementAndGet();
    }
    
    /**
     * Record job failed.
     */
    public void jobFailed() {
        activeJobs.decrementAndGet();
        totalJobsProcessed.incrementAndGet();
        failedJobs.incrementAndGet();
    }
    
    /**
     * Get current active job count.
     */
    public int getActiveJobCount() {
        return activeJobs.get();
    }
    
    /**
     * Get total jobs processed.
     */
    public int getTotalJobsProcessed() {
        return totalJobsProcessed.get();
    }
    
    /**
     * Get failed job count.
     */
    public int getFailedJobCount() {
        return failedJobs.get();
    }
    
    /**
     * Reset counters (for testing).
     */
    public void reset() {
        activeJobs.set(0);
        totalJobsProcessed.set(0);
        failedJobs.set(0);
    }
}
