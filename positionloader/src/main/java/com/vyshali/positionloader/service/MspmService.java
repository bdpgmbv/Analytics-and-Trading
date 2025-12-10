package com.vyshali.positionloader.service;

/*
 * AMPLIFIED: Added @Retry and @CircuitBreaker to external service calls
 *
 * BEFORE: No retry - single failure = EOD failure
 * AFTER:  3 retries with exponential backoff + circuit breaker
 *
 * WHY CRITICAL:
 * - MSPM is external system - network issues happen
 * - EOD runs once per day - must not fail due to transient errors
 * - Without retry, a 1-second network blip fails entire EOD
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.exception.MspmServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MspmService {

    private final WebClient mspmWebClient;

    @Value("${mspm.timeout:30s}")
    private Duration timeout;

    /**
     * Fetch EOD snapshot from MSPM.
     *
     * RETRY: 3 attempts with exponential backoff (1s, 2s, 4s)
     * CIRCUIT BREAKER: Opens after 5 failures in 60s, half-open after 30s
     *
     * @param accountId Account to fetch
     * @param businessDate EOD date
     * @return Snapshot with all positions
     * @throws MspmServiceException if all retries fail
     */
    @Retry(name = "mspm", fallbackMethod = "fetchSnapshotFallback")
    @CircuitBreaker(name = "mspm", fallbackMethod = "fetchSnapshotFallback")
    @Observed(name = "mspm.fetch.snapshot")
    public AccountSnapshotDTO fetchSnapshot(Integer accountId, LocalDate businessDate) {
        log.info("Fetching snapshot from MSPM: accountId={}, date={}", accountId, businessDate);

        return mspmWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/accounts/{accountId}/snapshot")
                        .queryParam("businessDate", businessDate)
                        .build(accountId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        Mono.error(new MspmServiceException(
                                "Client error from MSPM: " + response.statusCode(),
                                accountId,
                                false  // Don't retry client errors
                        )))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new MspmServiceException(
                                "Server error from MSPM: " + response.statusCode(),
                                accountId,
                                true  // Retry server errors
                        )))
                .bodyToMono(AccountSnapshotDTO.class)
                .timeout(timeout)
                .doOnError(e -> log.warn("MSPM call failed for account {}: {}", accountId, e.getMessage()))
                .block();
    }

    /**
     * Fetch intraday delta from MSPM.
     */
    @Retry(name = "mspm", fallbackMethod = "fetchDeltaFallback")
    @CircuitBreaker(name = "mspm", fallbackMethod = "fetchDeltaFallback")
    @Observed(name = "mspm.fetch.delta")
    public AccountSnapshotDTO fetchDelta(Integer accountId) {
        log.debug("Fetching intraday delta from MSPM: accountId={}", accountId);

        return mspmWebClient.get()
                .uri("/api/v1/accounts/{accountId}/delta", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        Mono.error(new MspmServiceException(
                                "Error from MSPM: " + response.statusCode(),
                                accountId,
                                response.statusCode().is5xxServerError()
                        )))
                .bodyToMono(AccountSnapshotDTO.class)
                .timeout(timeout)
                .block();
    }

    /**
     * Fallback when MSPM is completely unavailable.
     * Only called after all retries exhausted AND circuit is open.
     */
    private AccountSnapshotDTO fetchSnapshotFallback(Integer accountId, LocalDate businessDate, Exception e) {
        log.error("MSPM unavailable after retries for account {} on {}: {}",
                accountId, businessDate, e.getMessage());

        // Throw specific exception so caller knows to handle gracefully
        throw new MspmServiceException(
                "MSPM service unavailable after all retries: " + e.getMessage(),
                accountId,
                false
        );
    }

    private AccountSnapshotDTO fetchDeltaFallback(Integer accountId, Exception e) {
        log.error("MSPM delta unavailable for account {}: {}", accountId, e.getMessage());
        throw new MspmServiceException(
                "MSPM delta service unavailable: " + e.getMessage(),
                accountId,
                false
        );
    }
}