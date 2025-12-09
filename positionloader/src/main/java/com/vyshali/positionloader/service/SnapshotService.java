package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - Refactored for Non-Blocking I/O
 * FIXED: Added missing processEodFromMspm() and processIntradayPayload() methods
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

    // ============================================================
    // PUBLIC API METHODS
    // ============================================================

    /**
     * Main EOD Entry Point - Called by OpsController and MarketDataListener
     * Fetches data from MSPM and saves to database.
     * <p>
     * This is the method that was missing and causing compilation errors.
     */
    public void processEodFromMspm(Integer accountId) {
        log.info("Starting EOD processing for Account {}", accountId);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 1. Fetch from upstream (Network-bound, slow)
            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            // 2. Safety Check: Handle null/error from fallback
            if (snapshot == null) {
                log.error("ABORT: Upstream returned null for account {}. Skipping.", accountId);
                return;
            }

            if ("Unavailable".equalsIgnoreCase(snapshot.status()) || "ERROR".equalsIgnoreCase(snapshot.status())) {
                log.error("ABORT: Upstream data unavailable for {}. Status: {}. Skipping DB save.", accountId, snapshot.status());
                return;
            }

            // 3. Save to database (DB-bound, fast)
            saveSnapshotToDb(snapshot);

            log.info("EOD processing completed for Account {}", accountId);

        } catch (Exception e) {
            log.error("Failed to process EOD for account {}", accountId, e);
            throw e; // Re-throw so callers can handle appropriately
        } finally {
            sample.stop(meterRegistry.timer("posloader.eod.total_duration"));
        }
    }

    /**
     * Intraday Payload Processing - Called by OpsController for manual triggers
     * and MarketDataListener for Kafka messages.
     * <p>
     * This is the method that was missing and causing compilation errors.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processIntradayPayload(AccountSnapshotDTO snapshot) {
        log.info("Processing Intraday payload for Account {}", snapshot.accountId());

        // Validate input
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot: accountId is required");
        }

        // 1. Sanitize positions
        List<PositionDetailDTO> cleanPositions = mapper.sanitizeList(snapshot.positions());

        // 2. Upsert Reference Data
        upsertReferenceData(snapshot);

        // 3. Incremental Position Update (no batch rotation for intraday)
        if (cleanPositions != null && !cleanPositions.isEmpty()) {
            posRepo.batchIncrementalUpsert(snapshot.accountId(), cleanPositions, "MSPA_INTRADAY");
            log.info("Updated {} positions for account {}", cleanPositions.size(), snapshot.accountId());
        }

        // 4. Publish change event for downstream services
        eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), (cleanPositions != null ? cleanPositions.size() : 0), "INTRADAY_UPDATE");
    }

    /**
     * Alternative entry point that separates fetch from save.
     * Useful for scenarios where you want to control transaction boundaries.
     */
    public void initiateEodLoad(Integer accountId) {
        // Delegates to the main method
        processEodFromMspm(accountId);
    }

    // ============================================================
    // INTERNAL METHODS
    // ============================================================

    /**
     * Step 2: SAVE (Database Bound - Fast)
     * This transaction is short and safe.
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshotToDb(AccountSnapshotDTO snapshot) {
        log.info("Saving snapshot to DB for Account {}", snapshot.accountId());

        // 1. Sanitize
        List<PositionDetailDTO> cleanPositions = mapper.sanitizeList(snapshot.positions());

        // 2. Reference Data
        upsertReferenceData(snapshot);

        // 3. Create new batch for atomic swap
        int newBatchId = posRepo.createNextBatch(snapshot.accountId());

        // 4. Save Positions
        if (cleanPositions != null && !cleanPositions.isEmpty()) {
            posRepo.batchInsertPositions(snapshot.accountId(), cleanPositions, "MSPM_EOD", newBatchId);
            txnRepo.batchInsertTransactions(snapshot.accountId(), cleanPositions);
        } else {
            log.warn("Account {} has 0 positions.", snapshot.accountId());
        }

        // 5. Finalize - Atomic batch swap
        posRepo.activateBatch(snapshot.accountId(), newBatchId);
        posRepo.cleanUpArchivedBatches(snapshot.accountId());

        // 6. Enrich with exposure data
        exposureService.enrichSnapshot(snapshot.accountId());

        // 7. Track completion and publish events
        LocalDate today = LocalDate.now();
        trackerRepo.markAccountComplete(snapshot.accountId(), snapshot.clientId(), today);

        int posCount = (cleanPositions != null ? cleanPositions.size() : 0);
        eventPublisher.publishChangeEvent(snapshot.accountId(), snapshot.clientId(), posCount, "EOD_COMPLETE");

        // 8. Check if entire client is done
        if (trackerRepo.isClientFullyComplete(snapshot.clientId(), today)) {
            eventPublisher.publishReportingSignOff(snapshot.clientId(), today);
        }
    }

    /**
     * Process Trade Lifecycle Events (BUY, SELL, CANCEL, AMEND)
     */
    @Transactional
    public void processTradeEvent(TradeEventDTO trade) {
        log.info("Processing Lifecycle Event: {} for Account {}", trade.eventType(), trade.accountId());

        for (var pos : trade.positions()) {
            Integer productId = pos.productId();
            BigDecimal quantityDelta = pos.quantity();

            // Handle event type
            if ("CANCEL".equalsIgnoreCase(trade.eventType())) {
                // Cancel: Reverse the original quantity
                quantityDelta = quantityDelta.negate();
            } else if ("AMEND".equalsIgnoreCase(trade.eventType())) {
                // Amend: First reverse original, then apply new
                BigDecimal originalQty = txnRepo.findQuantityByRefId(trade.originalRefId());
                posRepo.upsertPositionQuantity(trade.accountId(), productId, originalQty.negate());
            }

            // Handle side (SELL reduces position)
            if ("SELL".equalsIgnoreCase(pos.txnType())) {
                quantityDelta = quantityDelta.negate();
            }

            // Apply position change
            posRepo.upsertPositionQuantity(trade.accountId(), productId, quantityDelta);

            // Get new total and publish event
            BigDecimal newTotalQty = posRepo.getPositionQuantity(trade.accountId(), productId);

            PositionChangeDTO changeEvent = new PositionChangeDTO(trade.accountId(), productId, newTotalQty);
            kafkaTemplate.send("POSITION_CHANGE_EVENTS", trade.accountId().toString(), changeEvent);
        }
    }

    /**
     * Helper: Ensure reference data exists before inserting positions
     */
    private void upsertReferenceData(AccountSnapshotDTO snapshot) {
        refRepo.ensureClientExists(snapshot.clientId(), snapshot.clientName());
        refRepo.ensureFundExists(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        refRepo.upsertAccount(snapshot.accountId(), snapshot.fundId(), snapshot.accountNumber(), snapshot.accountType());

        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            refRepo.batchUpsertProducts(snapshot.positions());
        }
    }
}