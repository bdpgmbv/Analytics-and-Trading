package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:46 PM
 * @author Vyshali Prabananth Lal
 */


/*
 * CRITICAL FIX #9: Resilient MSPM Service with Graceful Degradation
 *
 * Issue #1: "Prices going to zero as price service itself is down"
 *
 * Problem: MSPM down = Complete EOD failure
 *
 * Solution: Resilience4j patterns with cache fallback
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.metrics.LoaderMetrics;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class ResilientMspmService {

    private static final Logger log = LoggerFactory.getLogger(ResilientMspmService.class);

    private final MspmIntegrationService mspmService;
    private final PositionCacheService cacheService;
    private final LoaderMetrics metrics;

    public ResilientMspmService(MspmIntegrationService mspmService, PositionCacheService cacheService, LoaderMetrics metrics) {
        this.mspmService = mspmService;
        this.cacheService = cacheService;
        this.metrics = metrics;
    }

    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchWithCacheFallback")
    @Bulkhead(name = "mspm-service", fallbackMethod = "fetchWithCacheFallback")
    @RateLimiter(name = "mspm-service", fallbackMethod = "fetchWithCacheFallback")
    @TimeLimiter(name = "mspm-service", fallbackMethod = "fetchWithTimeoutFallback")
    public CompletableFuture<AccountSnapshotDTO> fetchEodSnapshotAsync(Integer accountId) {
        return CompletableFuture.supplyAsync(() -> {
            Instant start = Instant.now();

            try {
                log.debug("Fetching EOD snapshot for account {} from MSPM", accountId);
                AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

                if (snapshot != null && !"Unavailable".equals(snapshot.status())) {
                    cacheService.cacheSnapshot(snapshot);
                }

                Duration duration = Duration.between(start, Instant.now());
                metrics.recordMspmFetch(duration, true);

                return snapshot;

            } catch (Exception e) {
                Duration duration = Duration.between(start, Instant.now());
                metrics.recordMspmFetch(duration, false);
                throw e;
            }
        });
    }

    @CircuitBreaker(name = "mspm-service", fallbackMethod = "syncFetchFallback")
    @Bulkhead(name = "mspm-service", fallbackMethod = "syncFetchFallback")
    @RateLimiter(name = "mspm-service", fallbackMethod = "syncFetchFallback")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        Instant start = Instant.now();

        try {
            log.debug("Fetching EOD snapshot for account {} from MSPM", accountId);
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            if (snapshot != null && !"Unavailable".equals(snapshot.status())) {
                cacheService.cacheSnapshot(snapshot);
            }

            Duration duration = Duration.between(start, Instant.now());
            metrics.recordMspmFetch(duration, true);

            return snapshot;

        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            metrics.recordMspmFetch(duration, false);
            throw e;
        }
    }

    public CompletableFuture<AccountSnapshotDTO> fetchWithCacheFallback(Integer accountId, Throwable t) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("MSPM failed for account {}, attempting cache fallback: {}", accountId, t.getMessage());

            return getCachedWithStaleStatus(accountId, t.getMessage());
        });
    }

    public CompletableFuture<AccountSnapshotDTO> fetchWithTimeoutFallback(Integer accountId, Throwable t) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("MSPM timed out for account {}, attempting cache fallback", accountId);

            return getCachedWithStaleStatus(accountId, "MSPM timeout");
        });
    }

    public AccountSnapshotDTO syncFetchFallback(Integer accountId, Throwable t) {
        log.warn("MSPM failed for account {}, using sync fallback: {}", accountId, t.getMessage());
        return getCachedWithStaleStatus(accountId, t.getMessage());
    }

    private AccountSnapshotDTO getCachedWithStaleStatus(Integer accountId, String failureReason) {
        Optional<AccountSnapshotDTO> cached = cacheService.getSnapshot(accountId);

        if (cached.isPresent()) {
            AccountSnapshotDTO original = cached.get();

            log.info("Using STALE cached data for account {}. Original status: {}", accountId, original.status());

            return new AccountSnapshotDTO(original.clientId(), original.clientName(), original.fundId(), original.fundName(), original.baseCurrency(), original.accountId(), original.accountNumber(), original.accountType(), original.positions(), "STALE_CACHE");
        }

        log.error("No cached data available for account {} after MSPM failure", accountId);
        return new AccountSnapshotDTO(null, null, null, null, null, accountId, null, null, null, "UNAVAILABLE");
    }

    public FetchResult fetchWithFallbackStrategy(Integer accountId, FallbackStrategy strategy) {
        try {
            AccountSnapshotDTO snapshot = fetchEodSnapshot(accountId);
            return new FetchResult(snapshot, FetchSource.LIVE, null);

        } catch (Exception e) {
            log.warn("Primary fetch failed for account {}: {}", accountId, e.getMessage());

            switch (strategy) {
                case USE_CACHE:
                    Optional<AccountSnapshotDTO> cached = cacheService.getSnapshot(accountId);
                    if (cached.isPresent()) {
                        return new FetchResult(cached.get(), FetchSource.CACHE, e.getMessage());
                    }
                    return new FetchResult(null, FetchSource.NONE, e.getMessage());

                case FAIL_FAST:
                    return new FetchResult(null, FetchSource.NONE, e.getMessage());

                case RETRY_THEN_CACHE:
                    try {
                        Thread.sleep(1000);
                        AccountSnapshotDTO retried = mspmService.fetchEodSnapshot(accountId);
                        return new FetchResult(retried, FetchSource.LIVE_RETRY, null);
                    } catch (Exception retryEx) {
                        Optional<AccountSnapshotDTO> cachedRetry = cacheService.getSnapshot(accountId);
                        if (cachedRetry.isPresent()) {
                            return new FetchResult(cachedRetry.get(), FetchSource.CACHE, retryEx.getMessage());
                        }
                        return new FetchResult(null, FetchSource.NONE, retryEx.getMessage());
                    }

                default:
                    return new FetchResult(null, FetchSource.NONE, e.getMessage());
            }
        }
    }

    public boolean isMspmHealthy() {
        try {
            mspmService.fetchEodSnapshot(0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // DTOs
    public enum FallbackStrategy {
        USE_CACHE, FAIL_FAST, RETRY_THEN_CACHE
    }

    public enum FetchSource {
        LIVE, LIVE_RETRY, CACHE, NONE
    }

    public record FetchResult(AccountSnapshotDTO snapshot, FetchSource source, String errorMessage) {
        public boolean isSuccess() {
            return snapshot != null && !"UNAVAILABLE".equals(snapshot.status());
        }

        public boolean isStale() {
            return source == FetchSource.CACHE;
        }
    }
}
