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
    private final TransactionRepository txnRepo; // <--- NEW DEPENDENCY
    private final EodTrackerRepository trackerRepo;
    private final EventPublisherService eventPublisher;
    private final ExposureEnrichmentService exposureService; // <--- INJECTED
    private final MeterRegistry meterRegistry;

    /**
     * EOD Process:
     * 1. Fetch Data
     * 2. Upsert Reference Data
     * 3. Wipe Transactions (History) & Positions (State)
     * 4. Insert New Data
     * 5. Track & Notify
     */
    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Fetch
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            // 2. Reference Data
            upsertReferenceData(snapshot);

            // 3. Wipe Old Data
            txnRepo.deleteTransactionsByAccount(accountId);
            posRepo.deletePositionsByAccount(accountId);

            // 4. Load New Data
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                posRepo.batchInsertPositions(accountId, snapshot.positions(), "MSPM_EOD");
                txnRepo.batchInsertTransactions(accountId, snapshot.positions());

                // 5. ENRICHMENT (New Step)
                // Calculate Specific/Generic exposures based on the new positions
                exposureService.enrichSnapshot(accountId);
            }

            // 6. Complete
            LocalDate today = LocalDate.now();
            trackerRepo.markAccountComplete(accountId, snapshot.clientId(), today);

            int count = (snapshot.positions() == null) ? 0 : snapshot.positions().size();
            eventPublisher.publishChangeEvent(accountId, snapshot.clientId(), count, "EOD_COMPLETE");

            if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
                eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
            }
            log.info("EOD Snapshot completed for Account {}", accountId);

        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.duration"));
        }
    }

    /**
     * Intraday Process:
     * 1. Upsert Reference Data
     * 2. Incremental Update on Positions
     * 3. Notify
     */
    @Transactional(rollbackFor = Exception.class)
    public void processIntradayPayload(AccountSnapshotDTO snapshot) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            upsertReferenceData(snapshot);

            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                // Update State (Add/Subtract logic)
                posRepo.batchIncrementalUpsert(snapshot.accountId(), snapshot.positions(), "MSPA_INTRA");

                // Optional: You could also insert into TransactionRepo here if you want granular history
                // txnRepo.batchInsertTransactions(snapshot.accountId(), snapshot.positions());
            }

            int count = (snapshot.positions() == null) ? 0 : snapshot.positions().size();
            eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), count, "INTRADAY_UPDATE");

        } finally {
            sample.stop(meterRegistry.timer("posloader.intra.duration"));
        }
    }

    private void upsertReferenceData(AccountSnapshotDTO snapshot) {
        refRepo.ensureClientExists(snapshot.clientId(), snapshot.clientName());
        refRepo.ensureFundExists(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        refRepo.upsertAccount(snapshot.accountId(), snapshot.fundId(), snapshot.accountNumber(), snapshot.accountType());

        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            refRepo.batchUpsertProducts(snapshot.positions());
        }
    }

    // ... existing imports ...

    @Transactional
    public void processTradeEvent(TradeEventDTO trade) {
        for (var pos : trade.positions()) {
            // Simple lookup (In real app, query ReferenceDataRepo)
            // Hardcoding ID for demo flow to ensure it works without complex lookup logic
            Integer productId = 1001; // Mock Product ID for EURUSD

            // Handle Side (Sell = Negative Quantity)
            BigDecimal qty = pos.quantity();
            if ("SELL".equalsIgnoreCase(pos.txnType())) {
                qty = qty.negate();
            }

            posRepo.upsertPositionQuantity(trade.accountId(), productId, qty);
            log.info("DB UPDATED: Account {} Product {} Delta {}", trade.accountId(), productId, qty);
        }
    }
}