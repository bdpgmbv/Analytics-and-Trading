package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - Refactored for Non-Blocking I/O
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionChangeDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import com.vyshali.positionloader.dto.TradeEventDTO;
import com.vyshali.positionloader.mapper.SnapshotMapper;
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
    private final SnapshotMapper mapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Step 1: FETCH (Network Bound - Slow)
     * No @Transactional here! We don't want to hold a DB connection while waiting for the web.
     */
    public void initiateEodLoad(Integer accountId) {
        log.info("Step 1: Fetching Data for Account {}", accountId);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 1. Download Data (Takes 2-10 seconds)
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            // 2. Safety Check: If upstream failed (returned null), STOP.
            if (snapshot == null || "Unavailable".equals(snapshot.status())) {
                log.error("ABORT: Upstream data unavailable for {}. Skipping DB save.", accountId);
                return;
            }

            // 3. Pass valid data to the Transactional Saver (Takes 50ms)
            saveSnapshotToDb(snapshot);

        } catch (Exception e) {
            log.error("Failed to load snapshot for {}", accountId, e);
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.total_duration"));
        }
    }

    /**
     * Step 2: SAVE (Database Bound - Fast)
     * This transaction is short and safe.
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshotToDb(AccountSnapshotDTO snapshot) {
        log.info("Step 2: Saving Data to DB for {}", snapshot.accountId());

        // 1. Sanitize
        List<PositionDetailDTO> cleanPositions = mapper.sanitizeList(snapshot.positions());

        // 2. Reference Data
        upsertReferenceData(snapshot);

        // 3. Save Positions (Bitemporal)
        int newBatchId = posRepo.createNextBatch(snapshot.accountId()); // Kept for legacy compatibility

        if (cleanPositions != null && !cleanPositions.isEmpty()) {
            posRepo.batchInsertPositions(snapshot.accountId(), cleanPositions, "MSPM_EOD", newBatchId);
            txnRepo.batchInsertTransactions(snapshot.accountId(), cleanPositions);
        } else {
            log.warn("Account {} has 0 positions.", snapshot.accountId());
        }

        // 4. Finalize
        posRepo.activateBatch(snapshot.accountId(), newBatchId);
        posRepo.cleanUpArchivedBatches(snapshot.accountId());
        exposureService.enrichSnapshot(snapshot.accountId());

        // 5. Events
        LocalDate today = LocalDate.now();
        trackerRepo.markAccountComplete(snapshot.accountId(), snapshot.clientId(), today);
        eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), (cleanPositions != null ? cleanPositions.size() : 0), "EOD_COMPLETE");

        if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
            eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
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

            posRepo.upsertPositionQuantity(trade.accountId(), productId, quantityDelta);

            BigDecimal newTotalQty = posRepo.getPositionQuantity(trade.accountId(), productId);

            PositionChangeDTO changeEvent = new PositionChangeDTO(trade.accountId(), productId, newTotalQty);
            kafkaTemplate.send("POSITION_CHANGE_EVENTS", trade.accountId().toString(), changeEvent);
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