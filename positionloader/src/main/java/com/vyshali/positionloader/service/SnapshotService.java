package com.vyshali.positionloader.service;

import com.vyshali.fxanalyzer.common.entity.Account;
import com.vyshali.fxanalyzer.common.entity.Snapshot;
import com.vyshali.fxanalyzer.common.exception.EntityNotFoundException;
import com.vyshali.fxanalyzer.common.repository.AccountRepository;
import com.vyshali.fxanalyzer.common.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Service for managing position snapshots.
 * Handles creation, superseding, and retrieval of snapshots.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final AccountRepository accountRepository;

    /**
     * Create a new snapshot for an account.
     * Supersedes any existing active snapshot for the same account/date/type.
     */
    @Transactional
    public Snapshot createSnapshot(String accountNumber, String snapshotType, 
                                   LocalDate snapshotDate, LocalTime snapshotTime,
                                   String sourceSystem) {
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> EntityNotFoundException.account(accountNumber));
        
        // Supersede any existing active snapshots
        int superseded = snapshotRepository.supersedePreviousSnapshots(
                account.getAccountId(), snapshotType, snapshotDate);
        
        if (superseded > 0) {
            log.info("Superseded {} existing snapshot(s) for account {} on {}", 
                    superseded, accountNumber, snapshotDate);
        }
        
        // Create new snapshot
        Snapshot snapshot = Snapshot.builder()
                .account(account)
                .snapshotType(snapshotType)
                .snapshotDate(snapshotDate)
                .snapshotTime(snapshotTime != null ? snapshotTime : LocalTime.now())
                .status("ACTIVE")
                .sourceSystem(sourceSystem)
                .positionCount(0)
                .totalMvBase(BigDecimal.ZERO)
                .build();
        
        snapshot = snapshotRepository.save(snapshot);
        log.info("Created new snapshot {} for account {} on {}", 
                snapshot.getSnapshotId(), accountNumber, snapshotDate);
        
        return snapshot;
    }

    /**
     * Find or create a snapshot for the given parameters.
     */
    @Transactional
    public Snapshot findOrCreateSnapshot(String accountNumber, String snapshotType,
                                         LocalDate snapshotDate, LocalTime snapshotTime,
                                         String sourceSystem) {
        
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> EntityNotFoundException.account(accountNumber));
        
        Optional<Snapshot> existing = snapshotRepository.findActiveSnapshot(
                account.getAccountId(), snapshotType, snapshotDate);
        
        if (existing.isPresent()) {
            log.debug("Found existing snapshot {} for account {} on {}", 
                    existing.get().getSnapshotId(), accountNumber, snapshotDate);
            return existing.get();
        }
        
        return createSnapshot(accountNumber, snapshotType, snapshotDate, snapshotTime, sourceSystem);
    }

    /**
     * Update snapshot statistics after positions are loaded.
     */
    @Transactional
    public void updateSnapshotStats(Long snapshotId, int positionCount, BigDecimal totalMvBase) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> EntityNotFoundException.snapshot(snapshotId));
        
        snapshot.setPositionCount(positionCount);
        snapshot.setTotalMvBase(totalMvBase);
        snapshotRepository.save(snapshot);
        
        log.info("Updated snapshot {} stats: {} positions, {} total MV", 
                snapshotId, positionCount, totalMvBase);
    }

    /**
     * Find active snapshot for account and date.
     */
    public Optional<Snapshot> findActiveSnapshot(String accountNumber, String snapshotType, LocalDate snapshotDate) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return Optional.empty();
        }
        return snapshotRepository.findActiveSnapshot(account.getAccountId(), snapshotType, snapshotDate);
    }

    /**
     * Deactivate a snapshot (manual deactivation).
     */
    @Transactional
    public void deactivateSnapshot(Long snapshotId) {
        Snapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> EntityNotFoundException.snapshot(snapshotId));
        
        snapshot.setStatus("INACTIVE");
        snapshotRepository.save(snapshot);
        
        log.info("Deactivated snapshot {}", snapshotId);
    }

    /**
     * Clean up old snapshots.
     */
    @Transactional
    public int deleteOldSnapshots(int retentionDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        int deleted = snapshotRepository.deleteOldSnapshots(cutoffDate);
        log.info("Deleted {} snapshots older than {}", deleted, cutoffDate);
        return deleted;
    }
}
