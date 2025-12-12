package com.vyshali.positionloader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for cleaning up old snapshots.
 * Runs daily at 2 AM to remove snapshots older than retention period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotCleanupJob {

    private final SnapshotService snapshotService;

    @Value("${fxanalyzer.position.snapshot-retention-days:7}")
    private int retentionDays;

    /**
     * Daily cleanup of old snapshots.
     * Cron: 0 0 2 * * * = Every day at 2:00 AM
     */
    @Scheduled(cron = "${fxanalyzer.cleanup.cron:0 0 2 * * *}")
    public void cleanupOldSnapshots() {
        log.info("Starting scheduled snapshot cleanup - retention: {} days", retentionDays);
        
        try {
            int deleted = snapshotService.deleteOldSnapshots(retentionDays);
            log.info("Snapshot cleanup completed - {} snapshots deleted", deleted);
        } catch (Exception e) {
            log.error("Snapshot cleanup failed: {}", e.getMessage(), e);
        }
    }
}
