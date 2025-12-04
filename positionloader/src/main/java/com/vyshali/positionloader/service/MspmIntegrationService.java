package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:00
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient; // <--- Changed from RestTemplate

@Slf4j
@Service
@RequiredArgsConstructor
public class MspmIntegrationService {

    private final RestClient mspmClient; // <--- Matches your RestConfig bean

    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchEodFallback")
    @Retry(name = "mspm-service")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        String uri = "/accounts/" + accountId + "/eod-snapshot";
        log.info("Calling Upstream: {}", uri);

        // New Fluent API syntax
        return mspmClient.get().uri(uri).retrieve().body(AccountSnapshotDTO.class);
    }

    public AccountSnapshotDTO fetchEodFallback(Integer accountId, Throwable t) {
        log.error("MSPM Upstream is DOWN. Returning empty snapshot for account {}. Error: {}", accountId, t.getMessage());
        return new AccountSnapshotDTO(0, "Unavailable", 0, "Unavailable", "USD", accountId, "N/A", "ERROR", null);
    }
}