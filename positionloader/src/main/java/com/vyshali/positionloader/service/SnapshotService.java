package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.dto.*;
import com.vyshali.positionloader.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Core service for position snapshot processing.
 * Handles EOD loads, intraday updates, and trade events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmService mspm;
    private final ValidationService validation;
    private final PositionRepository positions;
    private final ReferenceDataRepository refData;
    private final TransactionRepository transactions;
    private final AuditRepository audit;
    private final KafkaTemplate<String, Object> kafka;

    // ==================== EOD PROCESSING ====================

    /**
     * Full EOD load: fetch from MSPM and save to DB.
     */
    public void processEod(Integer accountId) {
        log.info("Starting EOD for account {}", accountId);

        // 1. Fetch from upstream
        AccountSnapshotDTO snapshot = mspm.fetchSnapshot(accountId);

        // 2. Validate
        if (snapshot == null || !snapshot.isAvailable()) {
            log.error("MSPM unavailable for account {}, status: {}", accountId, snapshot != null ? snapshot.status() : "null");
            return;
        }

        // 3. Save to DB
        saveSnapshot(snapshot);
        log.info("EOD complete for account {}", accountId);
    }

    /**
     * Save snapshot to database (transactional).
     */
    @Transactional
    public void saveSnapshot(AccountSnapshotDTO snapshot) {
        Integer accountId = snapshot.accountId();
        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // 1. Upsert reference data
        refData.ensureReferenceData(snapshot);

        // 2. Create new batch
        int batchId = positions.createBatch(accountId);

        // 3. Save positions and transactions
        if (positionList != null && !positionList.isEmpty()) {
            positions.insertPositions(accountId, positionList, "MSPM_EOD", batchId);
            transactions.insertTransactions(accountId, positionList);
        }

        // 4. Activate batch (atomic swap)
        positions.activateBatch(accountId, batchId);
        positions.cleanupBatches(accountId);

        // 5. Track completion
        LocalDate today = LocalDate.now();
        audit.markAccountComplete(accountId, snapshot.clientId(), today);

        // 6. Publish events
        publishChange("EOD_COMPLETE", snapshot);

        // 7. Check client completion
        if (audit.isClientComplete(snapshot.clientId(), today)) {
            publishSignOff(snapshot.clientId(), today);
        }
    }

    // ==================== INTRADAY PROCESSING ====================

    /**
     * Process intraday position update (manual or Kafka).
     */
    @Transactional
    public void processIntraday(AccountSnapshotDTO snapshot) {
        log.info("Processing intraday for account {}", snapshot.accountId());

        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // Upsert reference data
        refData.ensureReferenceData(snapshot);

        // Incremental update (no batch rotation)
        if (positionList != null && !positionList.isEmpty()) {
            positions.insertPositions(snapshot.accountId(), positionList, "INTRADAY", 0);
        }

        publishChange("INTRADAY_UPDATE", snapshot);
    }

    /**
     * Process trade lifecycle event (BUY, SELL, CANCEL, AMEND).
     */
    @Transactional
    public void processTrade(TradeEventDTO trade) {
        log.info("Processing {} for account {}", trade.eventType(), trade.accountId());

        for (var leg : trade.legs()) {
            BigDecimal delta = leg.quantity();

            // Handle event type
            if (trade.isCancel()) {
                delta = delta.negate();
            } else if (trade.isAmend()) {
                BigDecimal original = transactions.getQuantityByRefId(trade.originalRefId());
                positions.updateQuantity(trade.accountId(), leg.productId(), original.negate());
            }

            // Handle side (SELL reduces position)
            if ("SELL".equalsIgnoreCase(leg.side())) {
                delta = delta.negate();
            }

            // Apply change
            positions.updateQuantity(trade.accountId(), leg.productId(), delta);

            // Publish quantity change
            BigDecimal newQty = positions.getQuantity(trade.accountId(), leg.productId());
            kafka.send(KafkaConfig.TOPIC_POSITION_CHANGES, trade.accountId().toString(), new Events.QuantityChange(trade.accountId(), leg.productId(), newQty));
        }
    }

    // ==================== HELPERS ====================

    private List<PositionDTO> sanitize(List<PositionDTO> list) {
        if (list == null) return null;
        return list.stream().filter(p -> p != null && p.productId() != null).toList();
    }

    private void publishChange(String eventType, AccountSnapshotDTO snapshot) {
        kafka.send(KafkaConfig.TOPIC_POSITION_CHANGES, snapshot.accountId().toString(), new Events.PositionChange(eventType, snapshot.accountId(), snapshot.clientId(), snapshot.positionCount()));
    }

    private void publishSignOff(Integer clientId, LocalDate date) {
        kafka.send(KafkaConfig.TOPIC_SIGNOFF, clientId.toString(), new Events.ClientSignOff(clientId, date, 0));
        log.info("SignOff published for client {}", clientId);
    }
}
