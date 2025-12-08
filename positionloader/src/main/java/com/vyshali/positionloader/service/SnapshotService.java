package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:02
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionChangeDTO; // <--- NEW
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate; // <--- NEW
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

    // Inject Kafka to send granular updates
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        // (Keep existing EOD logic: fetch, blue/green load, flip, enrich, notify)
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);
            upsertReferenceData(snapshot);

            int newBatchId = posRepo.createNextBatch(accountId);
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                posRepo.batchInsertPositions(accountId, snapshot.positions(), "MSPM_EOD", newBatchId);
                txnRepo.batchInsertTransactions(accountId, snapshot.positions());
            }
            posRepo.activateBatch(accountId, newBatchId);
            posRepo.cleanUpArchivedBatches(accountId);
            exposureService.enrichSnapshot(accountId);

            LocalDate today = LocalDate.now();
            trackerRepo.markAccountComplete(accountId, snapshot.clientId(), today);
            eventPublisher.publishChangeEvent(accountId, snapshot.clientId(), (snapshot.positions() != null ? snapshot.positions().size() : 0), "EOD_COMPLETE");

            if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
                eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
            }
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.duration"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIntradayPayload(AccountSnapshotDTO snapshot) {
        // (Keep existing intraday batch logic)
        upsertReferenceData(snapshot);
        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            posRepo.batchIncrementalUpsert(snapshot.accountId(), snapshot.positions(), "MSPA_INTRA");
        }
        // Note: For full consistency, we should also emit granular events here,
        // but for this demo we focus on the TradeEvent flow below.
        eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), (snapshot.positions() != null ? snapshot.positions().size() : 0), "INTRADAY_UPDATE");
    }

    @Transactional
    public void processTradeEvent(TradeEventDTO trade) {
        log.info("Processing Lifecycle Event: {} for Account {}", trade.eventType(), trade.accountId());
        for (var pos : trade.positions()) {
            Integer productId = pos.productId();
            BigDecimal quantityDelta = pos.quantity();

            // 1. Lifecycle Logic
            if ("CANCEL".equalsIgnoreCase(trade.eventType())) {
                quantityDelta = quantityDelta.negate();
            } else if ("AMEND".equalsIgnoreCase(trade.eventType())) {
                BigDecimal originalQty = txnRepo.findQuantityByRefId(trade.originalRefId());
                posRepo.upsertPositionQuantity(trade.accountId(), productId, originalQty.negate());
            }

            if ("SELL".equalsIgnoreCase(pos.txnType())) {
                quantityDelta = quantityDelta.negate();
            }

            // 2. Update DB
            posRepo.upsertPositionQuantity(trade.accountId(), productId, quantityDelta);

            // 3. FETCH NEW STATE (For Cache Consistency)
            BigDecimal newTotalQty = posRepo.getPositionQuantity(trade.accountId(), productId);

            // 4. PUBLISH GRANULAR EVENT (The "Feedback Loop")
            // This ensures Price Service gets the exact new quantity to re-value.
            PositionChangeDTO changeEvent = new PositionChangeDTO(trade.accountId(), productId, newTotalQty);
            kafkaTemplate.send("POSITION_CHANGE_EVENTS", trade.accountId().toString(), changeEvent);

            log.info("Published Position Update: Account {} Product {} NewQty {}", trade.accountId(), productId, newTotalQty);
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