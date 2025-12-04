package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:02
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmIntegrationService mspmService;
    private final ReferenceDataRepository refRepo;
    private final PositionRepository posRepo;
    private final TransactionRepository txnRepo;
    private final EodTrackerRepository trackerRepo;
    private final EventPublisherService eventPublisher;
    private final ExposureEnrichmentService exposureService;
    private final MeterRegistry meterRegistry;

    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. Fetch Data
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            // 2. Reference Data
            upsertReferenceData(snapshot);

            // 3. BLUE/GREEN: Create Staging Batch
            int newBatchId = posRepo.createNextBatch(accountId);
            log.info("Started EOD Load for Account {}. Staging Batch: {}", accountId, newBatchId);

            // 4. Load Data into Staging
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                // Insert into the invisible 'STAGING' batch
                posRepo.batchInsertPositions(accountId, snapshot.positions(), "MSPM_EOD", newBatchId);

                // Transactions are historical/append-only, so we record them directly
                txnRepo.batchInsertTransactions(accountId, snapshot.positions());
            }

            // 5. BLUE/GREEN: Atomic Flip
            posRepo.activateBatch(accountId, newBatchId);
            log.info("Activated Batch {} for Account {}", newBatchId, accountId);

            // 6. Cleanup Old Data
            posRepo.cleanUpArchivedBatches(accountId);

            // 7. Enrichment (Calculates risk on the now-active positions)
            exposureService.enrichSnapshot(accountId);

            // 8. Sign-off & Notify
            LocalDate today = LocalDate.now();
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

            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                // Incremental upsert finds the ACTIVE batch internally
                posRepo.batchIncrementalUpsert(snapshot.accountId(), snapshot.positions(), "MSPA_INTRA");
            }

            int count = (snapshot.positions() == null) ? 0 : snapshot.positions().size();
            eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), count, "INTRADAY_UPDATE");

        } finally {
            sample.stop(meterRegistry.timer("posloader.intra.duration"));
        }
    }

    @Transactional
    public void processTradeEvent(TradeEventDTO trade) {
        log.info("Processing Lifecycle Event: {} for Account {}", trade.eventType(), trade.accountId());
        for (var pos : trade.positions()) {
            Integer productId = pos.productId();
            BigDecimal quantityDelta = pos.quantity();

            if ("CANCEL".equalsIgnoreCase(trade.eventType())) {
                quantityDelta = quantityDelta.negate();
            } else if ("AMEND".equalsIgnoreCase(trade.eventType())) {
                BigDecimal originalQty = txnRepo.findQuantityByRefId(trade.originalRefId());
                posRepo.upsertPositionQuantity(trade.accountId(), productId, originalQty.negate());
            }

            if ("SELL".equalsIgnoreCase(pos.txnType())) {
                quantityDelta = quantityDelta.negate();
            }

            // Updates the ACTIVE batch
            posRepo.upsertPositionQuantity(trade.accountId(), productId, quantityDelta);
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
}