package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - NEW: Redis-based idempotency service
 *
 * PURPOSE:
 * Prevent duplicate processing of messages across pod restarts.
 * Uses Redis with TTL to track processed external reference IDs.
 *
 * BEFORE (in-memory):
 * - Pod restarts → all tracking lost → duplicates possible
 *
 * AFTER (Redis):
 * - Pod restarts → Redis still has keys → no duplicates
 *
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redis;

    // Redis key prefix for idempotency tracking
    private static final String KEY_PREFIX = "posloader:idem:";

    // Default TTL for idempotency keys (1 hour)
    @Value("${features.idempotency.ttl-minutes:60}")
    private int ttlMinutes;

    @Value("${features.idempotency.enabled:true}")
    private boolean enabled;

    // Fallback to in-memory if Redis is unavailable
    private final Set<String> fallbackSet = ConcurrentHashMap.newKeySet();
    private volatile boolean redisAvailable = true;

    /**
     * Check if an external reference ID has already been processed.
     *
     * @param externalRefId The unique external reference ID
     * @return true if already processed (duplicate), false if new
     */
    public boolean isDuplicate(String externalRefId) {
        if (!enabled || externalRefId == null || externalRefId.isBlank()) {
            return false;
        }

        String key = KEY_PREFIX + externalRefId;

        try {
            if (redisAvailable) {
                Boolean exists = redis.hasKey(key);
                if (Boolean.TRUE.equals(exists)) {
                    log.debug("Duplicate detected (Redis): {}", externalRefId);
                    return true;
                }
            } else {
                // Fallback to in-memory
                if (fallbackSet.contains(externalRefId)) {
                    log.debug("Duplicate detected (fallback): {}", externalRefId);
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            log.warn("Redis error checking duplicate, using fallback: {}", e.getMessage());
            redisAvailable = false;
            return fallbackSet.contains(externalRefId);
        }
    }

    /**
     * Mark an external reference ID as processed.
     *
     * @param externalRefId The unique external reference ID
     */
    public void markProcessed(String externalRefId) {
        if (!enabled || externalRefId == null || externalRefId.isBlank()) {
            return;
        }

        String key = KEY_PREFIX + externalRefId;
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        try {
            if (redisAvailable) {
                redis.opsForValue().set(key, "1", ttl);
                log.debug("Marked processed (Redis): {} with TTL {}min", externalRefId, ttlMinutes);
            } else {
                fallbackSet.add(externalRefId);
                cleanupFallbackIfNeeded();
            }

        } catch (Exception e) {
            log.warn("Redis error marking processed, using fallback: {}", e.getMessage());
            redisAvailable = false;
            fallbackSet.add(externalRefId);
            cleanupFallbackIfNeeded();
        }
    }

    /**
     * Check and mark in a single atomic operation.
     * Returns true if this is a NEW (non-duplicate) entry.
     *
     * @param externalRefId The unique external reference ID
     * @return true if new and marked, false if duplicate
     */
    public boolean checkAndMark(String externalRefId) {
        if (!enabled || externalRefId == null || externalRefId.isBlank()) {
            return true; // Allow processing if disabled or no ref ID
        }

        String key = KEY_PREFIX + externalRefId;
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        try {
            if (redisAvailable) {
                // SETNX (SET if Not eXists) - atomic operation
                Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", ttl);
                if (Boolean.TRUE.equals(isNew)) {
                    log.debug("New entry marked (Redis): {}", externalRefId);
                    return true;
                } else {
                    log.debug("Duplicate blocked (Redis): {}", externalRefId);
                    return false;
                }
            } else {
                // Fallback: not truly atomic but best effort
                if (fallbackSet.add(externalRefId)) {
                    cleanupFallbackIfNeeded();
                    return true;
                }
                return false;
            }

        } catch (Exception e) {
            log.warn("Redis error in checkAndMark, using fallback: {}", e.getMessage());
            redisAvailable = false;
            if (fallbackSet.add(externalRefId)) {
                cleanupFallbackIfNeeded();
                return true;
            }
            return false;
        }
    }

    /**
     * Batch check for duplicates.
     * Returns list of NEW (non-duplicate) reference IDs.
     */
    public List<String> filterDuplicates(Collection<String> externalRefIds) {
        if (!enabled || externalRefIds == null || externalRefIds.isEmpty()) {
            return List.copyOf(externalRefIds);
        }

        return externalRefIds.stream().filter(refId -> refId != null && !refId.isBlank()).filter(refId -> !isDuplicate(refId)).toList();
    }

    /**
     * Batch mark as processed.
     */
    public void markProcessedBatch(Collection<String> externalRefIds) {
        if (!enabled || externalRefIds == null || externalRefIds.isEmpty()) {
            return;
        }

        externalRefIds.stream().filter(refId -> refId != null && !refId.isBlank()).forEach(this::markProcessed);
    }

    /**
     * Remove a reference ID (for testing or manual cleanup).
     */
    public void remove(String externalRefId) {
        if (externalRefId == null) return;

        String key = KEY_PREFIX + externalRefId;
        try {
            redis.delete(key);
            fallbackSet.remove(externalRefId);
        } catch (Exception e) {
            log.warn("Error removing idempotency key: {}", e.getMessage());
            fallbackSet.remove(externalRefId);
        }
    }

    /**
     * Get count of tracked keys (for monitoring).
     */
    public long getTrackedCount() {
        try {
            Set<String> keys = redis.keys(KEY_PREFIX + "*");
            return keys != null ? keys.size() : fallbackSet.size();
        } catch (Exception e) {
            return fallbackSet.size();
        }
    }

    /**
     * Check if Redis is available.
     */
    public boolean isRedisAvailable() {
        try {
            redis.getConnectionFactory().getConnection().ping();
            redisAvailable = true;
            return true;
        } catch (Exception e) {
            redisAvailable = false;
            return false;
        }
    }

    /**
     * Cleanup fallback set to prevent memory issues.
     */
    private void cleanupFallbackIfNeeded() {
        if (fallbackSet.size() > 100_000) {
            log.warn("Fallback idempotency set exceeds 100k, clearing oldest entries");
            // Simple cleanup: clear half (not ideal but prevents OOM)
            int toRemove = fallbackSet.size() / 2;
            fallbackSet.stream().limit(toRemove).toList().forEach(fallbackSet::remove);
        }
    }

    /**
     * Clear all idempotency tracking (use with caution!).
     */
    public void clearAll() {
        try {
            Set<String> keys = redis.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Error clearing Redis keys: {}", e.getMessage());
        }
        fallbackSet.clear();
        log.info("Cleared all idempotency tracking");
    }
}