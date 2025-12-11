package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.config.LoaderProperties;
import com.fxanalyzer.positionloader.repository.ArchivalRepository;
import com.fxanalyzer.positionloader.repository.AuditRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Position archival service (Phase 4 #18).
 * 
 * Handles:
 * - Archiving old positions to archive table
 * - Cleanup of archived data
 * - Scheduled archival jobs
 */
@Service
public class ArchivalService {
    
    private static final Logger log = LoggerFactory.getLogger(ArchivalService.class);
    
    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int ARCHIVE_BATCH_SIZE = 10000;
    
    private final ArchivalRepository archivalRepository;
    private final AuditRepository auditRepository;
    private final LoaderProperties loaderProperties;
    
    public ArchivalService(
            ArchivalRepository archivalRepository,
            AuditRepository auditRepository,
            LoaderProperties loaderProperties) {
        this.archivalRepository = archivalRepository;
        this.auditRepository = auditRepository;
        this.loaderProperties = loaderProperties;
    }
    
    /**
     * Archive positions older than retention period.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "archiveOldPositions", lockAtMostFor = "PT1H")
    @Transactional
    public void archiveOldPositions() {
        if (!loaderProperties.featureFlags().archivalEnabled()) {
            log.debug("Archival is disabled");
            return;
        }
        
        LocalDate cutoffDate = LocalDate.now().minusDays(DEFAULT_RETENTION_DAYS);
        log.info("Starting position archival for dates before {}", cutoffDate);
        
        int totalArchived = 0;
        int batchArchived;
        
        do {
            batchArchived = archivalRepository.archiveBatch(cutoffDate, ARCHIVE_BATCH_SIZE);
            totalArchived += batchArchived;
            
            if (batchArchived > 0) {
                log.debug("Archived {} positions (total: {})", batchArchived, totalArchived);
            }
        } while (batchArchived == ARCHIVE_BATCH_SIZE);
        
        if (totalArchived > 0) {
            log.info("Archived {} positions older than {}", totalArchived, cutoffDate);
            auditRepository.logEvent("POSITION_ARCHIVAL", 
                null, 
                "system",
                "Archived " + totalArchived + " positions older than " + cutoffDate);
        }
    }
    
    /**
     * Archive positions for a specific account.
     */
    @Transactional
    public int archiveAccountPositions(int accountId, LocalDate olderThan) {
        log.info("Archiving positions for account {} older than {}", accountId, olderThan);
        
        int archived = archivalRepository.archiveByAccount(accountId, olderThan);
        
        if (archived > 0) {
            auditRepository.logEvent("POSITION_ARCHIVAL", 
                String.valueOf(accountId), 
                "system",
                "Archived " + archived + " positions older than " + olderThan);
        }
        
        return archived;
    }
    
    /**
     * Delete archived positions older than extended retention.
     * Runs weekly on Sunday at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @SchedulerLock(name = "purgeArchivedPositions", lockAtMostFor = "PT2H")
    @Transactional
    public void purgeArchivedPositions() {
        // Keep archived positions for 1 year
        LocalDate cutoffDate = LocalDate.now().minusYears(1);
        log.info("Purging archived positions older than {}", cutoffDate);
        
        int purged = archivalRepository.purgeArchived(cutoffDate);
        
        if (purged > 0) {
            log.info("Purged {} archived positions older than {}", purged, cutoffDate);
            auditRepository.logEvent("ARCHIVE_PURGE", 
                null, 
                "system",
                "Purged " + purged + " archived positions older than " + cutoffDate);
        }
    }
    
    /**
     * Get archival statistics.
     */
    public ArchivalStats getStats() {
        return archivalRepository.getStats();
    }
    
    public record ArchivalStats(
        long totalArchived,
        LocalDate oldestArchive,
        LocalDate newestArchive,
        long totalSizeBytes
    ) {}
}
