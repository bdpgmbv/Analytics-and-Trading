package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - Added Safe Fallback
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class MspmIntegrationService {

    private final RestClient mspmClient;

    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchEodFallback")
    @Retry(name = "mspm-service")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        String uri = "/accounts/" + accountId + "/eod-snapshot";
        log.info("Calling Upstream: {}", uri);
        return mspmClient.get().uri(uri).retrieve().body(AccountSnapshotDTO.class);
    }

    // *** SAFE FALLBACK ***
    public AccountSnapshotDTO fetchEodFallback(Integer accountId, Throwable t) {
        log.error("CRITICAL: MSPM Upstream is DOWN. Aborting load for account {}. Error: {}", accountId, t.getMessage());
        // Return null to signal the calling service to STOP.
        // Do NOT return an empty DTO, or the system will think the client has 0 positions.
        return null;
    }
}