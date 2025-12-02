package com.vyshali.positionloader.service;

/*
 * 12/02/2025 - 11:18 AM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationJob {
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "DailyRecon", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void runDailyReconciliation() {
        log.info("Starting Daily Reconciliation...");
        Integer localCount = jdbcTemplate.queryForObject("SELECT count(*) FROM Positions", Integer.class);
        log.info("Total Positions in DB: {}", localCount);
        if (localCount != null && localCount == 0) {
            log.error("ALERT: Zero positions found in DB!");
        }
    }
}
