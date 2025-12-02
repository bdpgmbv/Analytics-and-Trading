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
public class MspmIntegrationService {
    private final RestClient mspmClient;

    @Retry(name = "mspmService")
    public AccountSnapshotDTO fetchEodSnapshot(Integer accountId) {
        return mspmClient.get().uri("/accounts/{id}/eod", accountId).retrieve().body(AccountSnapshotDTO.class);
    }
}
