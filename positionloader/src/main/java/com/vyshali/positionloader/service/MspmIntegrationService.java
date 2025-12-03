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
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MspmIntegrationService {

    private final RestTemplate restTemplate;

    // Matches config in application.yml
    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchEodFallback")
    @Retry(name = "mspm-service")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        String url = "http://mock-upstream:8081/mspm/accounts/" + accountId + "/eod-snapshot";
        log.info("Calling Upstream: {}", url);
        return restTemplate.getForObject(url, AccountSnapshotDTO.class);
    }

    // FALLBACK: If Upstream is down, return a safe default or cached version
    public AccountSnapshotDTO fetchEodFallback(Integer accountId, Throwable t) {
        log.error("MSPM Upstream is DOWN. Returning empty snapshot for account {}. Error: {}", accountId, t.getMessage());

        // In Prod, you might load from a local cache or return a specific error DTO
        return new AccountSnapshotDTO(0, "Unavailable", 0, "Unavailable", "USD", accountId, "N/A", "ERROR", null);
    }
}