package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:00
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class MspaIntegrationService {
    private final RestClient mspaClient;

    @Retry(name = "mspaService")
    public AccountSnapshotDTO fetchIntradayPositions(Integer accountId) {
        return mspaClient.get().uri("/analysis/{id}/positions", accountId).retrieve().body(AccountSnapshotDTO.class);
    }
}
