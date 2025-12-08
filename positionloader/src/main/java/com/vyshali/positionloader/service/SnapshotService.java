package com.vyshali.positionloader.service;

/*
 * 12/1/25 - 23:02
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionChangeDTO;
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import com.vyshali.positionloader.mapper.SnapshotMapper; // <--- NEW
import com.vyshali.positionloader.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    private final SnapshotMapper mapper; // <--- NEW: Decoupling Mapper

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void processEodFromMspm(Integer accountId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            // 1. Sanitize Data using Mapper (Decoupling)
            List<PositionDetailDTO> cleanPositions = mapper.sanitizeList(snapshot.positions());

            upsertReferenceData(snapshot);

            int newBatchId = posRepo.createNextBatch(accountId);
            if (cleanPositions != null && !cleanPositions.isEmpty()) {
                // Use Clean Positions
                posRepo.batchInsertPositions(accountId, cleanPositions, "MSPM_EOD", newBatchId);
                txnRepo.batchInsertTransactions(accountId, cleanPositions);
            }
            posRepo.activateBatch(accountId, newBatchId);
            posRepo.cleanUpArchivedBatches(accountId);
            exposureService.enrichSnapshot(accountId);

            LocalDate today = LocalDate.now();
            trackerRepo.markAccountComplete(accountId, snapshot.clientId(), today);
            eventPublisher.publishChangeEvent(accountId, snapshot.clientId(), (cleanPositions != null ? cleanPositions.size() : 0), "EOD_COMPLETE");

            if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
                eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
            }
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.duration"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void processIntradayPayload(AccountSnapshotDTO snapshot) {
        upsertReferenceData(snapshot);

        List<PositionDetailDTO> cleanPositions = mapper.sanitizeList(snapshot.positions());

        if (cleanPositions != null && !cleanPositions.isEmpty()) {
            posRepo.batchIncrementalUpsert(snapshot.accountId(), cleanPositions, "MSPA_INTRA");
        }
        eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), (cleanPositions != null ? cleanPositions.size() : 0), "INTRADAY_UPDATE");
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

            posRepo.upsertPositionQuantity(trade.accountId(), productId, quantityDelta);

            BigDecimal newTotalQty = posRepo.getPositionQuantity(trade.accountId(), productId);

            PositionChangeDTO changeEvent = new PositionChangeDTO(trade.accountId(), productId, newTotalQty);
            kafkaTemplate.send("POSITION_CHANGE_EVENTS", trade.accountId().toString(), changeEvent);

            log.info("Published Position Update: Account {} Product {} NewQty {}", trade.accountId(), productId, newTotalQty);
        }
    }

    private void upsertReferenceData(AccountSnapshotDTO snapshot) {
        refRepo.ensureClientExists(snapshot.clientId(), snapshot.clientName());
        refRepo.ensureFundExists(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        refRepo.upsertAccount(snapshot.accountId(), snapshot.fundId(), snapshot.accountNumber(), snapshot.accountType());
        // Note: Reference data usually safe to use raw snapshot, but can be sanitized if needed
        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            refRepo.batchUpsertProducts(snapshot.positions());
        }
    }
}