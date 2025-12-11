package com.vyshali.positionloader.health;

/*
 * 12/11/2025 - 2:24 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoaderHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    // Track consecutive failures
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // Track last successful EOD
    private final AtomicReference<Instant> lastSuccessfulEod = new AtomicReference<>(Instant.now());

    // Track active jobs
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean healthy = true;

        // Check 1: Database connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                details.put("database", "UP");
            } else {
                details.put("database", "DOWN - connection invalid");
                healthy = false;
            }
        } catch (Exception e) {
            details.put("database", "DOWN - " + e.getMessage());
            healthy = false;
        }

        // Check 2: Consecutive failures
        int failures = consecutiveFailures.get();
        details.put("consecutiveFailures", failures);
        if (failures >= 5) {
            details.put("failureAlert", "CRITICAL - 5+ consecutive failures");
            healthy = false;
        }

        // Check 3: Active jobs count
        int jobs = activeJobs.get();
        details.put("activeJobs", jobs);

        // Check 4: EOD window check (5 PM - 8 PM)
        int hour = LocalTime.now().getHour();
        boolean inEodWindow = hour >= 17 && hour <= 20;
        details.put("inEodWindow", inEodWindow);

        if (inEodWindow) {
            Instant lastSuccess = lastSuccessfulEod.get();
            long minutesSinceSuccess = Duration.between(lastSuccess, Instant.now()).toMinutes();
            details.put("minutesSinceLastEodSuccess", minutesSinceSuccess);

            // If in EOD window and no success in 30 minutes, flag as warning
            if (minutesSinceSuccess > 30) {
                details.put("eodAlert", "WARNING - No EOD success in 30+ minutes during EOD window");
                // Don't mark unhealthy, just warn - EOD might not have started yet
            }
        }

        return healthy ? Health.up().withDetails(details).build() : Health.down().withDetails(details).build();
    }

    // Called by PositionService on successful EOD
    public void recordEodSuccess() {
        consecutiveFailures.set(0);
        lastSuccessfulEod.set(Instant.now());
        log.debug("Health: EOD success recorded");
    }

    // Called by PositionService on failed EOD
    public void recordEodFailure() {
        int count = consecutiveFailures.incrementAndGet();
        log.warn("Health: EOD failure recorded. Consecutive failures: {}", count);
    }

    // Called by KafkaListeners when job starts
    public void jobStarted() {
        activeJobs.incrementAndGet();
    }

    // Called by KafkaListeners when job ends
    public void jobEnded() {
        activeJobs.decrementAndGet();
    }

    // For shutdown handler to check
    public int getActiveJobCount() {
        return activeJobs.get();
    }
}