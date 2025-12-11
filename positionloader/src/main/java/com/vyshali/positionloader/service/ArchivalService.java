package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.positionloader.repository.ArchivalRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service for position archival operations (Phase 4 #22).
 */
@Service
public class ArchivalService {
    
    private static final Logger log = LoggerFactory.getLogger(ArchivalService.class);
    
    private static final int DEFAULT_ARCHIVE_DAYS = 90;
    private static final int DEFAULT_PURGE_DAYS = 365;
    
    private final ArchivalRepository archivalRepository;
    private final LoaderConfig config;
    private final Counter positionsArchived;
    private final Counter positionsPurged;
    
    public ArchivalService(
            ArchivalRepository archivalRepository,
            LoaderConfig config,
            MeterRegistry meterRegistry) {
        this.archivalRepository = archivalRepository;
        this.config = config;
        this.positionsArchived = Counter.builder("positions.archived")
            .description("Positions archived")
            .register(meterRegistry);
        this.positionsPurged = Counter.builder("positions.purged")
            .description("Positions purged from archive")
            .register(meterRegistry);
    }
    
    /**
     * Archive positions older than configured days.
     */
    @Transactional
    public ArchiveResult archiveOldPositions() {
        if (!config.features().archivalEnabled()) {
            log.debug("Archival feature is disabled");
            return ArchiveResult.disabled();
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(DEFAULT_ARCHIVE_DAYS);
        return archivePositions(cutoffDate);
    }
    
    /**
     * Archive positions older than specified date.
     */
    @Transactional
    public ArchiveResult archivePositions(LocalDate olderThan) {
        log.info("Starting archival for positions older than {}", olderThan);
        
        try {
            int archived = archivalRepository.archivePositions(olderThan);
            positionsArchived.increment(archived);
            
            log.info("Archived {} positions", archived);
            return ArchiveResult.success(archived);
            
        } catch (Exception e) {
            log.error("Archival failed", e);
            return ArchiveResult.failed(e.getMessage());
        }
    }
    
    /**
     * Archive positions for specific account.
     */
    @Transactional
    public ArchiveResult archiveAccountPositions(int accountId, LocalDate olderThan) {
        log.info("Starting archival for account {} older than {}", accountId, olderThan);
        
        try {
            int archived = archivalRepository.archiveAccountPositions(accountId, olderThan);
            positionsArchived.increment(archived);
            
            log.info("Archived {} positions for account {}", archived, accountId);
            return ArchiveResult.success(archived);
            
        } catch (Exception e) {
            log.error("Archival failed for account {}", accountId, e);
            return ArchiveResult.failed(e.getMessage());
        }
    }
    
    /**
     * Restore positions from archive.
     */
    @Transactional
    public int restorePositions(int accountId, LocalDate businessDate) {
        log.info("Restoring positions for account {} date {}", accountId, businessDate);
        return archivalRepository.restorePositions(accountId, businessDate);
    }
    
    /**
     * Purge old archived data.
     */
    @Transactional
    public int purgeOldArchive() {
        if (!config.features().archivalEnabled()) {
            return 0;
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(DEFAULT_PURGE_DAYS);
        return purgeArchive(cutoffDate);
    }
    
    /**
     * Purge archive older than specified date.
     */
    @Transactional
    public int purgeArchive(LocalDate olderThan) {
        log.info("Purging archive older than {}", olderThan);
        
        int purged = archivalRepository.purgeArchive(olderThan);
        positionsPurged.increment(purged);
        
        log.info("Purged {} positions from archive", purged);
        return purged;
    }
    
    /**
     * Get archive statistics.
     */
    public ArchiveStats getArchiveStats() {
        long count = archivalRepository.getArchiveCount();
        var dateRange = archivalRepository.getArchiveDateRange();
        
        return new ArchiveStats(count, dateRange.minDate(), dateRange.maxDate());
    }
    
    /**
     * Get archive statistics for account.
     */
    public ArchiveStats getArchiveStats(int accountId) {
        long count = archivalRepository.getArchiveCount(accountId);
        return new ArchiveStats(count, null, null);
    }
    
    /**
     * Scheduled archival job - runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledArchival() {
        log.info("Running scheduled archival job");
        archiveOldPositions();
    }
    
    /**
     * Scheduled purge job - runs weekly on Sunday at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void scheduledPurge() {
        log.info("Running scheduled purge job");
        purgeOldArchive();
    }
    
    /**
     * Archive result.
     */
    public record ArchiveResult(
        Status status,
        int count,
        String error
    ) {
        public enum Status {
            SUCCESS, DISABLED, FAILED
        }
        
        public static ArchiveResult success(int count) {
            return new ArchiveResult(Status.SUCCESS, count, null);
        }
        
        public static ArchiveResult disabled() {
            return new ArchiveResult(Status.DISABLED, 0, null);
        }
        
        public static ArchiveResult failed(String error) {
            return new ArchiveResult(Status.FAILED, 0, error);
        }
        
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
    
    /**
     * Archive statistics.
     */
    public record ArchiveStats(
        long totalCount,
        LocalDate oldestDate,
        LocalDate newestDate
    ) {}
}
