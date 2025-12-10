package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:59 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled background jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobs {

    private final JdbcTemplate jdbc;

    /**
     * Daily reconciliation at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "DailyRecon", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void dailyReconciliation() {
        log.info("Starting daily reconciliation");

        Integer positionCount = jdbc.queryForObject("SELECT COUNT(*) FROM Positions WHERE system_to = '9999-12-31 23:59:59'", Integer.class);

        log.info("Active positions: {}", positionCount);

        if (positionCount != null && positionCount == 0) {
            log.error("ALERT: Zero active positions in database!");
        }

        // Add more checks as needed
        Integer orphanBatches = jdbc.queryForObject("SELECT COUNT(*) FROM Account_Batches WHERE status = 'STAGING' AND created_at < NOW() - INTERVAL '1 day'", Integer.class);

        if (orphanBatches != null && orphanBatches > 0) {
            log.warn("Found {} orphan staging batches", orphanBatches);
        }
    }
}
