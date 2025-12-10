package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.FeatureFlags;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Simple caching service for snapshots and positions.
 */
@Slf4j
@Service
public class CacheService {

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper json;
    private final FeatureFlags flags;

    public CacheService(RedisTemplate<String, String> redis, ObjectMapper json, FeatureFlags flags) {
        this.redis = redis;
        this.json = json;
        this.flags = flags;
    }

    // ==================== SNAPSHOT CACHE ====================

    public void saveSnapshot(AccountSnapshotDTO snapshot) {
        if (!flags.getCache().isEnabled() || snapshot == null) return;

        try {
            String key = "snapshot:" + snapshot.accountId();
            redis.opsForValue().set(key, json.writeValueAsString(snapshot), Duration.ofHours(flags.getCache().getSnapshotTtlHours()));
            log.debug("Cached snapshot for account {}", snapshot.accountId());
        } catch (Exception e) {
            log.warn("Failed to cache snapshot: {}", e.getMessage());
        }
    }

    public Optional<AccountSnapshotDTO> getSnapshot(Integer accountId) {
        if (!flags.getCache().isEnabled()) return Optional.empty();

        try {
            String key = "snapshot:" + accountId;
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(json.readValue(cached, AccountSnapshotDTO.class));
            }
        } catch (Exception e) {
            log.warn("Cache read error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ==================== POSITION CACHE ====================

    public void savePositions(Integer accountId, LocalDate date, List<PositionDTO> positions) {
        if (!flags.getCache().isEnabled() || positions == null) return;

        try {
            String key = "positions:" + accountId + ":" + date;
            redis.opsForValue().set(key, json.writeValueAsString(positions), Duration.ofHours(flags.getCache().getPositionTtlHours()));
        } catch (Exception e) {
            log.warn("Failed to cache positions: {}", e.getMessage());
        }
    }

    public Optional<List<PositionDTO>> getPositions(Integer accountId, LocalDate date) {
        if (!flags.getCache().isEnabled()) return Optional.empty();

        try {
            String key = "positions:" + accountId + ":" + date;
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(json.readValue(cached, json.getTypeFactory().constructCollectionType(List.class, PositionDTO.class)));
            }
        } catch (Exception e) {
            log.warn("Cache read error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // ==================== INVALIDATION ====================

    public void invalidate(Integer accountId) {
        if (!flags.getCache().isEnabled()) return;

        redis.delete("snapshot:" + accountId);
        var keys = redis.keys("positions:" + accountId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        log.debug("Invalidated cache for account {}", accountId);
    }
}
