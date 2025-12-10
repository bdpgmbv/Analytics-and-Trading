package com.vyshali.positionloader.service;

/*
 * SIMPLIFIED: Only for position file uploads
 *
 * PURPOSE:
 * Prevent users from uploading the same file twice via REST API.
 *
 * NOT USED FOR:
 * - Kafka messages (Kafka offsets handle this)
 * - EOD processing (once daily per account)
 *
 * HOW IT WORKS:
 * 1. User uploads file
 * 2. We create a key from: accountId + file hash or position count
 * 3. Redis SETNX checks if key exists
 * 4. If exists = duplicate, reject
 * 5. If not exists = new upload, allow
 * 6. Key expires after 1 hour (TTL)
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "posloader:upload:";

    @Value("${features.idempotency.ttl-minutes:60}")
    private int ttlMinutes;

    /**
     * Check if this upload is a duplicate.
     *
     * @param accountId      The account being uploaded to
     * @param fileIdentifier Something unique about the file (hash, name, or position count)
     * @return true if NEW upload (allow), false if DUPLICATE (reject)
     */
    public boolean isNewUpload(Integer accountId, String fileIdentifier) {
        if (accountId == null || fileIdentifier == null) {
            return true; // Allow if missing data
        }

        String key = KEY_PREFIX + accountId + ":" + fileIdentifier;
        Duration ttl = Duration.ofMinutes(ttlMinutes);

        try {
            // SETNX - atomic "set if not exists"
            Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", ttl);

            if (Boolean.TRUE.equals(isNew)) {
                log.info("New upload allowed: account={}, file={}", accountId, fileIdentifier);
                return true;
            } else {
                log.warn("Duplicate upload blocked: account={}, file={}", accountId, fileIdentifier);
                return false;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, allowing upload: {}", e.getMessage());
            return true; // If Redis fails, allow (DB will handle duplicates)
        }
    }

    /**
     * Generate a file identifier from file content.
     * Uses filename + size as a simple identifier.
     */
    public String generateFileIdentifier(String filename, long fileSize) {
        return filename + "-" + fileSize;
    }

    /**
     * Generate identifier from position data.
     * Uses position count + first product ID as identifier.
     */
    public String generatePositionIdentifier(int positionCount, Integer firstProductId) {
        return positionCount + "-" + (firstProductId != null ? firstProductId : "0");
    }
}