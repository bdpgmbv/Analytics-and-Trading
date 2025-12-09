package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:46 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #8: Position Caching Service
 *
 * Issue #8: "Caching that we had for positions were not good"
 *
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import com.vyshali.positionloader.metrics.LoaderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class PositionCacheService {

    private static final Logger log = LoggerFactory.getLogger(PositionCacheService.class);

    private static final String POSITION_KEY_PREFIX = "positions:account:";
    private static final String SNAPSHOT_KEY_PREFIX = "snapshot:account:";
    private static final String LAST_UPDATE_KEY_PREFIX = "lastUpdate:account:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final LoaderMetrics metrics;

    @Value("${cache.positions.ttl-hours:24}")
    private int positionTtlHours;

    @Value("${cache.snapshot.ttl-hours:4}")
    private int snapshotTtlHours;

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    public PositionCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, LoaderMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public Optional<List<PositionDetailDTO>> getPositions(Integer accountId, LocalDate businessDate) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        String key = buildPositionKey(accountId, businessDate);

        try {
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                metrics.recordCacheHit();
                log.debug("Cache HIT for positions: account={}, date={}", accountId, businessDate);

                List<PositionDetailDTO> positions = objectMapper.readValue(cached, objectMapper.getTypeFactory().constructCollectionType(List.class, PositionDetailDTO.class));
                return Optional.of(positions);
            }

            metrics.recordCacheMiss();
            log.debug("Cache MISS for positions: account={}, date={}", accountId, businessDate);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Cache read error for account {}: {}", accountId, e.getMessage());
            metrics.recordCacheMiss();
            return Optional.empty();
        }
    }

    public void cachePositions(Integer accountId, LocalDate businessDate, List<PositionDetailDTO> positions) {
        if (!cacheEnabled || positions == null) {
            return;
        }

        String key = buildPositionKey(accountId, businessDate);

        try {
            String json = objectMapper.writeValueAsString(positions);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(positionTtlHours));

            String updateKey = LAST_UPDATE_KEY_PREFIX + accountId;
            redisTemplate.opsForValue().set(updateKey, String.valueOf(System.currentTimeMillis()));

            log.debug("Cached {} positions for account {}", positions.size(), accountId);

        } catch (JsonProcessingException e) {
            log.warn("Failed to cache positions for account {}: {}", accountId, e.getMessage());
        }
    }

    public void invalidatePositions(Integer accountId) {
        if (!cacheEnabled) return;

        String pattern = POSITION_KEY_PREFIX + accountId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Invalidated {} position cache entries for account {}", keys.size(), accountId);
        }
    }

    public void invalidatePositions(Integer accountId, LocalDate businessDate) {
        if (!cacheEnabled) return;

        String key = buildPositionKey(accountId, businessDate);
        redisTemplate.delete(key);
        log.debug("Invalidated position cache for account {} date {}", accountId, businessDate);
    }

    public Optional<AccountSnapshotDTO> getSnapshot(Integer accountId) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        String key = SNAPSHOT_KEY_PREFIX + accountId;

        try {
            String cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                metrics.recordCacheHit();
                AccountSnapshotDTO snapshot = objectMapper.readValue(cached, AccountSnapshotDTO.class);
                return Optional.of(snapshot);
            }

            metrics.recordCacheMiss();
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Cache read error for snapshot {}: {}", accountId, e.getMessage());
            return Optional.empty();
        }
    }

    public void cacheSnapshot(AccountSnapshotDTO snapshot) {
        if (!cacheEnabled || snapshot == null) {
            return;
        }

        String key = SNAPSHOT_KEY_PREFIX + snapshot.accountId();

        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(snapshotTtlHours));
            log.debug("Cached snapshot for account {}", snapshot.accountId());

        } catch (JsonProcessingException e) {
            log.warn("Failed to cache snapshot for account {}: {}", snapshot.accountId(), e.getMessage());
        }
    }

    public void invalidateSnapshot(Integer accountId) {
        if (!cacheEnabled) return;

        String key = SNAPSHOT_KEY_PREFIX + accountId;
        redisTemplate.delete(key);
    }

    public Optional<Long> getLastUpdateTime(Integer accountId) {
        String key = LAST_UPDATE_KEY_PREFIX + accountId;
        String value = redisTemplate.opsForValue().get(key);

        if (value != null) {
            return Optional.of(Long.parseLong(value));
        }
        return Optional.empty();
    }

    public boolean isCacheStale(Integer accountId, long maxAgeMs) {
        return getLastUpdateTime(accountId).map(lastUpdate -> System.currentTimeMillis() - lastUpdate > maxAgeMs).orElse(true);
    }

    public void clearAllCaches(Integer accountId) {
        invalidatePositions(accountId);
        invalidateSnapshot(accountId);
        redisTemplate.delete(LAST_UPDATE_KEY_PREFIX + accountId);
        log.info("Cleared all caches for account {}", accountId);
    }

    public void warmCache(List<Integer> accountIds, LocalDate businessDate, java.util.function.Function<Integer, List<PositionDetailDTO>> positionLoader) {
        log.info("Warming cache for {} accounts", accountIds.size());

        int warmed = 0;
        for (Integer accountId : accountIds) {
            try {
                List<PositionDetailDTO> positions = positionLoader.apply(accountId);
                cachePositions(accountId, businessDate, positions);
                warmed++;
            } catch (Exception e) {
                log.warn("Failed to warm cache for account {}: {}", accountId, e.getMessage());
            }
        }

        log.info("Cache warming complete: {}/{} accounts", warmed, accountIds.size());
    }

    public CacheStats getCacheStats() {
        long positionKeys = countKeys(POSITION_KEY_PREFIX + "*");
        long snapshotKeys = countKeys(SNAPSHOT_KEY_PREFIX + "*");

        return new CacheStats(positionKeys, snapshotKeys, cacheEnabled);
    }

    private long countKeys(String pattern) {
        var keys = redisTemplate.keys(pattern);
        return keys != null ? keys.size() : 0;
    }

    private String buildPositionKey(Integer accountId, LocalDate businessDate) {
        return POSITION_KEY_PREFIX + accountId + ":" + businessDate;
    }

    public record CacheStats(long positionCacheEntries, long snapshotCacheEntries, boolean cacheEnabled) {
    }
}