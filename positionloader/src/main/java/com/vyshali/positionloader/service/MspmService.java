package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * MSPM integration service with circuit breaker.
 */
@Slf4j
@Service
public class MspmService {

    private final RestClient mspmClient;

    public MspmService(@Qualifier("mspmClient") RestClient mspmClient) {
        this.mspmClient = mspmClient;
    }

    /**
     * Fetch EOD snapshot from MSPM.
     */
    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchFallback")
    public AccountSnapshotDTO fetchSnapshot(Integer accountId) {
        log.info("Fetching snapshot for account {} from MSPM", accountId);

        return mspmClient.get().uri("/accounts/{id}/eod-snapshot", accountId).retrieve().body(AccountSnapshotDTO.class);
    }

    /**
     * Fallback when MSPM is unavailable.
     */
    public AccountSnapshotDTO fetchFallback(Integer accountId, Throwable ex) {
        log.error("MSPM unavailable for account {}: {}", accountId, ex.getMessage());
        throw new RuntimeException("MSPM unavailable: " + ex.getMessage(), ex);
    }
}