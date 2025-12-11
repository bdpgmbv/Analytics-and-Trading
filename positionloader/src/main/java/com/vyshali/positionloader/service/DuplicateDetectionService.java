package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.exception.DuplicateSnapshotException;
import com.vyshali.positionloader.repository.SnapshotHashRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Service for detecting duplicate position snapshots (Phase 4 #19).
 * Uses content hashing to detect when position data hasn't changed.
 */
@Service
public class DuplicateDetectionService {
    
    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);
    
    private final SnapshotHashRepository hashRepository;
    private final Counter duplicatesDetected;
    private final Counter uniqueSnapshots;
    
    public DuplicateDetectionService(
            SnapshotHashRepository hashRepository,
            MeterRegistry meterRegistry) {
        this.hashRepository = hashRepository;
        this.duplicatesDetected = Counter.builder("positions.duplicates.detected")
            .description("Number of duplicate snapshots detected")
            .register(meterRegistry);
        this.uniqueSnapshots = Counter.builder("positions.snapshots.unique")
            .description("Number of unique snapshots processed")
            .register(meterRegistry);
    }
    
    /**
     * Check if this snapshot is a duplicate of a previously processed one.
     */
    public boolean isDuplicate(int accountId, LocalDate businessDate, List<PositionDto> positions) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        
        String currentHash = computeHash(positions);
        String storedHash = hashRepository.findHash(accountId, businessDate);
        
        if (storedHash != null && storedHash.equals(currentHash)) {
            log.info("Duplicate detected for account {} date {}: hash={}", 
                accountId, businessDate, currentHash.substring(0, 16));
            duplicatesDetected.increment();
            return true;
        }
        
        return false;
    }
    
    /**
     * Check for duplicate and throw exception if found.
     */
    public void checkDuplicate(int accountId, LocalDate businessDate, List<PositionDto> positions) {
        if (isDuplicate(accountId, businessDate, positions)) {
            String hash = computeHash(positions);
            throw new DuplicateSnapshotException(
                "Duplicate snapshot detected for account " + accountId + " date " + businessDate,
                accountId, hash);
        }
    }
    
    /**
     * Store hash for future duplicate detection.
     */
    public void storeHash(int accountId, LocalDate businessDate, List<PositionDto> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        
        String hash = computeHash(positions);
        BigDecimal totalQuantity = positions.stream()
            .map(PositionDto::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMarketValue = positions.stream()
            .map(PositionDto::marketValueBase)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        hashRepository.upsertHashWithMetrics(
            accountId, businessDate, hash, positions.size(), 
            totalQuantity, totalMarketValue);
        
        uniqueSnapshots.increment();
        log.debug("Stored hash for account {} date {}: hash={}", 
            accountId, businessDate, hash.substring(0, 16));
    }
    
    /**
     * Clear stored hash (for reprocessing).
     */
    public void clearHash(int accountId, LocalDate businessDate) {
        hashRepository.deleteHash(accountId, businessDate);
        log.debug("Cleared hash for account {} date {}", accountId, businessDate);
    }
    
    /**
     * Compute content hash for a list of positions.
     * Hash is based on sorted position data to ensure consistency.
     */
    public String computeHash(List<PositionDto> positions) {
        if (positions == null || positions.isEmpty()) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Sort positions for consistent hashing
            positions.stream()
                .sorted(Comparator.comparing(PositionDto::productId)
                    .thenComparing(PositionDto::positionType))
                .forEach(pos -> {
                    String data = String.format("%d|%s|%s|%s|%s",
                        pos.productId(),
                        pos.quantity().toPlainString(),
                        pos.price().toPlainString(),
                        pos.currency(),
                        pos.positionType());
                    digest.update(data.getBytes(StandardCharsets.UTF_8));
                });
            
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Get hash info for account and date.
     */
    public SnapshotHashRepository.SnapshotHashInfo getHashInfo(int accountId, LocalDate businessDate) {
        return hashRepository.getHashInfo(accountId, businessDate);
    }
    
    /**
     * Purge old hashes.
     */
    public int purgeOldHashes(int daysOld) {
        int purged = hashRepository.purgeOld(daysOld);
        log.info("Purged {} old snapshot hashes", purged);
        return purged;
    }
}
