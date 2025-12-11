package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.repository.SnapshotHashRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Duplicate detection service (Phase 4 #19).
 * 
 * Detects duplicate position snapshots using content hashing.
 * Prevents reprocessing identical data.
 */
@Service
public class DuplicateDetectionService {
    
    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);
    
    private final SnapshotHashRepository hashRepository;
    private final Cache<String, String> hashCache;
    
    public DuplicateDetectionService(
            SnapshotHashRepository hashRepository,
            Cache<String, String> snapshotHashCache) {
        this.hashRepository = hashRepository;
        this.hashCache = snapshotHashCache;
    }
    
    /**
     * Compute SHA-256 hash of position content.
     * 
     * Positions are sorted by product ID for consistent hashing.
     */
    public String computeHash(List<PositionDto> positions) {
        if (positions == null || positions.isEmpty()) {
            return "EMPTY";
        }
        
        // Sort for consistent ordering
        List<PositionDto> sorted = positions.stream()
            .sorted(Comparator.comparing(PositionDto::productId))
            .toList();
        
        // Build content string
        StringBuilder content = new StringBuilder();
        for (PositionDto p : sorted) {
            content.append(p.productId())
                .append("|")
                .append(p.quantity())
                .append("|")
                .append(p.price())
                .append("|")
                .append(p.marketValueLocal())
                .append("\n");
        }
        
        // Compute hash
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    
    /**
     * Check if this is a duplicate snapshot.
     */
    public boolean isDuplicate(int accountId, LocalDate businessDate, String contentHash) {
        String cacheKey = accountId + "-" + businessDate;
        
        // Check cache first
        String cachedHash = hashCache.getIfPresent(cacheKey);
        if (cachedHash != null && cachedHash.equals(contentHash)) {
            log.debug("Duplicate detected in cache: {}", cacheKey);
            return true;
        }
        
        // Check database
        String storedHash = hashRepository.findHash(accountId, businessDate);
        if (storedHash != null && storedHash.equals(contentHash)) {
            log.debug("Duplicate detected in database: {}", cacheKey);
            // Update cache
            hashCache.put(cacheKey, storedHash);
            return true;
        }
        
        return false;
    }
    
    /**
     * Store hash for future comparisons.
     */
    public void storeHash(int accountId, LocalDate businessDate, String contentHash, 
            int positionCount) {
        
        String cacheKey = accountId + "-" + businessDate;
        
        // Store in database
        hashRepository.upsertHash(accountId, businessDate, contentHash, positionCount);
        
        // Update cache
        hashCache.put(cacheKey, contentHash);
        
        log.debug("Stored snapshot hash: account={} date={} hash={}", 
            accountId, businessDate, contentHash.substring(0, 16) + "...");
    }
    
    /**
     * Clear hash (for reprocessing).
     */
    public void clearHash(int accountId, LocalDate businessDate) {
        String cacheKey = accountId + "-" + businessDate;
        hashCache.invalidate(cacheKey);
        hashRepository.deleteHash(accountId, businessDate);
        log.info("Cleared snapshot hash: account={} date={}", accountId, businessDate);
    }
}
