package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * MSPM integration with resilience patterns and caching fallback.
 * Combines: MspmIntegrationService + ResilientMspmService
 */
@Slf4j
@Service
public class MspmService {

    private final RestClient mspmClient;
    private final CacheService cache;

    public MspmService(@Qualifier("mspmClient") RestClient mspmClient, CacheService cache) {
        this.mspmClient = mspmClient;
        this.cache = cache;
    }

    /**
     * Fetch EOD snapshot with circuit breaker and fallback to cache.
     */
    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchFromCache")
    @RateLimiter(name = "mspm-service")
    public AccountSnapshotDTO fetchSnapshot(Integer accountId) {
        log.debug("Fetching snapshot for account {} from MSPM", accountId);

        AccountSnapshotDTO snapshot = mspmClient.get().uri("/accounts/{id}/eod-snapshot", accountId).retrieve().body(AccountSnapshotDTO.class);

        if (snapshot != null && snapshot.isAvailable()) {
            cache.saveSnapshot(snapshot);
        }

        return snapshot;
    }

    /**
     * Fallback: return cached data when MSPM is unavailable.
     */
    public AccountSnapshotDTO fetchFromCache(Integer accountId, Throwable ex) {
        log.warn("MSPM failed for account {}, using cache fallback: {}", accountId, ex.getMessage());

        Optional<AccountSnapshotDTO> cached = cache.getSnapshot(accountId);

        if (cached.isPresent()) {
            AccountSnapshotDTO original = cached.get();
            log.info("Returning STALE cached data for account {}", accountId);

            // Return with STALE status
            return new AccountSnapshotDTO(original.accountId(), original.clientId(), original.clientName(), original.fundId(), original.fundName(), original.baseCurrency(), original.accountNumber(), original.accountType(), original.positions(), "STALE_CACHE");
        }

        log.error("No cached data available for account {} after MSPM failure", accountId);
        return new AccountSnapshotDTO(accountId, null, null, null, null, null, null, null, null, "UNAVAILABLE");
    }

    /**
     * Health check for MSPM service.
     */
    public boolean isHealthy() {
        try {
            mspmClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
