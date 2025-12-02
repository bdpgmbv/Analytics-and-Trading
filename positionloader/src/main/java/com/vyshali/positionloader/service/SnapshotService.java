package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:02
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final MspmIntegrationService mspmService;
    private final ReferenceDataRepository refRepo;
    private final PositionRepository posRepo;
    private final EodTrackerRepository trackerRepo;
    private final EventPublisherService eventPublisher;
    private final MeterRegistry meterRegistry;

    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);
            upsertReferenceData(snapshot);

            posRepo.deletePositionsByAccount(accountId);

            // FIX: .getPositions() -> .positions()
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                posRepo.batchInsertPositions(accountId, snapshot.positions(), "MSPM_EOD");
            }

            LocalDate today = LocalDate.now();
            // FIX: .getClientId() -> .clientId()
            trackerRepo.markAccountComplete(accountId, snapshot.clientId(), today);

            int count = (snapshot.positions() == null) ? 0 : snapshot.positions().size();
            eventPublisher.publishChangeEvent(accountId, snapshot.clientId(), count, "EOD_COMPLETE");

            if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
                eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
            }
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.duration"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIntradayPayload(AccountSnapshotDTO snapshot) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            upsertReferenceData(snapshot);
            // FIX: .getPositions() -> .positions()
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                // FIX: .getAccountId() -> .accountId()
                posRepo.batchIncrementalUpsert(snapshot.accountId(), snapshot.positions(), "MSPA_INTRA");
            }

            int count = (snapshot.positions() == null) ? 0 : snapshot.positions().size();
            eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), count, "INTRADAY_UPDATE");
        } finally {
            sample.stop(meterRegistry.timer("posloader.intra.duration"));
        }
    }

    private void upsertReferenceData(AccountSnapshotDTO snapshot) {
        // FIX: All getters updated to record style
        refRepo.ensureClientExists(snapshot.clientId(), snapshot.clientName());
        refRepo.ensureFundExists(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        refRepo.upsertAccount(snapshot.accountId(), snapshot.fundId(), snapshot.accountNumber(), snapshot.accountType());

        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            refRepo.batchUpsertProducts(snapshot.positions());
        }
    }
}