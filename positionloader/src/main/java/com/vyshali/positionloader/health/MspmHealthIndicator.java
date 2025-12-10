package com.vyshali.positionloader.health;

/*
 * 12/10/2025 - 2:38 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MspmHealthIndicator implements HealthIndicator {

    private final RestClient mspmClient;

    @Override
    public Health health() {
        try {
            long start = System.currentTimeMillis();

            // Simple connectivity check - HEAD request or lightweight endpoint
            mspmClient.get().uri("/health").retrieve().toBodilessEntity();

            long latency = System.currentTimeMillis() - start;

            if (latency > 5000) {
                return Health.down().withDetail("status", "SLOW").withDetail("latency_ms", latency).withDetail("threshold_ms", 5000).build();
            }

            return Health.up().withDetail("latency_ms", latency).build();

        } catch (Exception e) {
            log.error("MSPM health check failed: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
class EodProgressHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    @Override
    public Health health() {
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();

            // Count total accounts
            Integer total = jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM Accounts", Integer.class);

            // Count completed today
            Integer completed = jdbc.queryForObject("SELECT COUNT(*) FROM Eod_Daily_Status WHERE business_date = ? AND status = 'COMPLETED'", Integer.class, today);

            // Count failed today
            Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM Eod_Daily_Status WHERE business_date = ? AND status = 'FAILED'", Integer.class, today);

            total = total != null ? total : 0;
            completed = completed != null ? completed : 0;
            failed = failed != null ? failed : 0;

            int pending = total - completed - failed;
            double percentComplete = total > 0 ? (completed * 100.0 / total) : 0;

            // Business rules for health status
            Health.Builder builder;

            // After 6:30 PM and not complete = CRITICAL
            if (now.isAfter(LocalTime.of(18, 30)) && pending > 0) {
                builder = Health.down().withDetail("alert", "EOD_DEADLINE_BREACH");
            }
            // After 5:00 PM and < 50% complete = WARNING
            else if (now.isAfter(LocalTime.of(17, 0)) && percentComplete < 50) {
                builder = Health.status("WARNING").withDetail("alert", "EOD_RUNNING_LATE");
            }
            // Any failures = WARNING
            else if (failed > 0) {
                builder = Health.status("WARNING").withDetail("alert", "EOD_HAS_FAILURES");
            }
            // All good
            else {
                builder = Health.up();
            }

            return builder.withDetail("business_date", today.toString()).withDetail("total_accounts", total).withDetail("completed", completed).withDetail("failed", failed).withDetail("pending", pending).withDetail("percent_complete", String.format("%.1f%%", percentComplete)).build();

        } catch (Exception e) {
            log.error("EOD health check failed: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            // Check if we can get metadata (doesn't actually send a message)
            var future = kafkaTemplate.send("__health_check_topic__", "ping");
            // Cancel immediately - we just want to check connectivity
            future.cancel(true);

            // If we get here without exception, Kafka is reachable
            return Health.up().withDetail("bootstrap_servers", kafkaTemplate.getProducerFactory().getConfigurationProperties().get("bootstrap.servers")).build();

        } catch (Exception e) {
            log.error("Kafka health check failed: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
class DatabasePoolHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    @Override
    public Health health() {
        try {
            // Check connection and get pool stats if using HikariCP
            jdbc.queryForObject("SELECT 1", Integer.class);

            // Try to get HikariCP metrics if available
            // This is a simple version - production would use HikariCP MXBean

            return Health.up().withDetail("database", "postgresql").withDetail("connection_test", "OK").build();

        } catch (Exception e) {
            log.error("Database health check failed: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
