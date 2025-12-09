package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - Added Safe Fallback
 * FIXED: Fallback now returns a proper error DTO instead of null
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class MspmIntegrationService {

    private final RestClient mspmClient;

    /**
     * Fetch EOD Snapshot from upstream MSPM service
     *
     * @param accountId The account to fetch
     * @return AccountSnapshotDTO with data, or error status if upstream fails
     */
    @CircuitBreaker(name = "mspm-service", fallbackMethod = "fetchEodFallback")
    @Retry(name = "mspm-service")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        String uri = "/accounts/" + accountId + "/eod-snapshot";
        log.info("Calling Upstream MSPM: {}", uri);

        AccountSnapshotDTO response = mspmClient.get().uri(uri).retrieve().body(AccountSnapshotDTO.class);

        if (response != null) {
            log.info("Received snapshot for account {} with {} positions", accountId, response.positions() != null ? response.positions().size() : 0);
        }

        return response;
    }

    /**
     * SAFE FALLBACK - Returns an error DTO instead of null
     * <p>
     * This prevents NullPointerExceptions downstream and allows
     * the calling service to properly handle the failure.
     * <p>
     * FIXED: Now returns a proper DTO with status="Unavailable" instead of null
     */
    public AccountSnapshotDTO fetchEodFallback(Integer accountId, Throwable t) {
        log.error("CRITICAL: MSPM Upstream is DOWN. Cannot load account {}. Error: {}", accountId, t.getMessage());

        // Return an error DTO that the caller can check
        // Status = "Unavailable" signals that this data should NOT be saved
        return new AccountSnapshotDTO(0,                          // clientId
                "Unavailable",              // clientName
                0,                          // fundId
                "Unavailable",              // fundName
                "USD",                      // baseCurrency
                accountId,                  // accountId (preserve for logging)
                "N/A",                      // accountNumber
                "ERROR",                    // accountType
                Collections.emptyList(),    // positions (empty - DO NOT SAVE)
                "Unavailable"               // status - signals error condition
        );
    }

    /**
     * Health check for upstream MSPM service
     */
    public boolean isUpstreamHealthy() {
        try {
            mspmClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("MSPM health check failed: {}", e.getMessage());
            return false;
        }
    }
}