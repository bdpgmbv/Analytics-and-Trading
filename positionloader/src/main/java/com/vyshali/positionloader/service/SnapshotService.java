package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:02
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmIntegrationService mspmService;
    private final MspaIntegrationService mspaService;
    private final ReferenceDataRepository refRepo;
    private final PositionRepository posRepo;
    private final MeterRegistry meterRegistry;

    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("EOD: Start for Account: {}", accountId);
        try {
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);
            upsertReferenceData(snapshot);

            posRepo.deletePositionsByAccount(accountId);

            if (snapshot.getPositions() != null && !snapshot.getPositions().isEmpty()) {
                posRepo.batchInsertPositions(accountId, snapshot.getPositions(), "MSPM_EOD");
            }
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.duration"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIntradayFromMspa(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("INTRADAY: Start for Account: {}", accountId);
        try {
            AccountSnapshotDTO snapshot = mspaService.fetchIntradayPositions(accountId);
            upsertReferenceData(snapshot);

            if (snapshot.getPositions() != null && !snapshot.getPositions().isEmpty()) {
                posRepo.batchUpsertPositions(accountId, snapshot.getPositions(), "MSPA_INTRA");
            }
        } finally {
            sample.stop(meterRegistry.timer("posloader.intra.duration"));
        }
    }

    private void upsertReferenceData(AccountSnapshotDTO snapshot) {
        refRepo.ensureClientExists(snapshot.getClientId(), snapshot.getClientName());
        refRepo.ensureFundExists(snapshot.getFundId(), snapshot.getClientId(), snapshot.getFundName(), snapshot.getBaseCurrency());
        refRepo.upsertAccount(snapshot.getAccountId(), snapshot.getFundId(), snapshot.getAccountNumber(), snapshot.getAccountType());
        if (snapshot.getPositions() != null && !snapshot.getPositions().isEmpty()) {
            refRepo.batchUpsertProducts(snapshot.getPositions());
        }
    }
}