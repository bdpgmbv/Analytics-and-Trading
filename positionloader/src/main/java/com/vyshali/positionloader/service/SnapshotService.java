package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.config.KafkaConfig;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.Events;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.dto.ValidationResult;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import com.vyshali.positionloader.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Core service for position snapshot processing.
 * <p>
 * Handles three flows:
 * 1. EOD Load: Full position refresh from MSPM (after market close)
 * 2. Intraday Batch: Incremental updates from MSPA (throughout the day, 1-100 per batch)
 * 3. Manual Upload: Position uploads from FXAN UI (for non-FS accounts)
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
    private final ObjectMapper json;

    // ==================== FLOW 1: EOD PROCESSING ====================

    /**
     * Process EOD for an account.
     * Called when MSPM sends EOD trigger after market close.
     * <p>
     * Steps:
     * 1. Fetch full snapshot from MSPM REST API
     * 2. Validate positions (check for zero prices)
     * 3. Save to database (atomic batch swap)
     * 4. Check if client complete (all accounts done)
     * 5. Publish change event
     */
    public void processEod(Integer accountId) {
        log.info("Starting EOD: account={}", accountId);

        // 1. Fetch from MSPM
        AccountSnapshotDTO snapshot = mspm.fetchSnapshot(accountId);
        if (snapshot == null || !snapshot.isAvailable()) {
            String status = snapshot != null ? snapshot.status() : "null";
            log.error("MSPM unavailable: account={}, status={}", accountId, status);
            throw new RuntimeException("MSPM unavailable: " + status);
        }

        // 2. Validate
        ValidationResult result = validation.validate(snapshot);
        if (result.hasErrors()) {
            log.warn("Validation issues: account={}, errors={}", accountId, result.errorSummary());
            // Continue but log warnings
        }

        // 3. Save to DB
        saveEodSnapshot(snapshot);

        log.info("EOD complete: account={}, positions={}", accountId, snapshot.positionCount());
    }

    /**
     * Save EOD snapshot with atomic batch swap.
     */
    @Transactional
    public void saveEodSnapshot(AccountSnapshotDTO snapshot) {
        Integer accountId = snapshot.accountId();
        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // 1. Upsert reference data (Client → Fund → Account → Products)
        refData.ensureReferenceData(snapshot);

        // 2. Create new batch (staging)
        int batchId = positions.createBatch(accountId);

        // 3. Insert positions into new batch
        if (positionList != null && !positionList.isEmpty()) {
            positions.insertPositions(accountId, positionList, "MSPM_EOD", batchId);
            transactions.insertTransactions(accountId, positionList);
        }

        // 4. Activate batch (atomic swap)
        positions.activateBatch(accountId, batchId);
        positions.cleanupBatches(accountId);

        // 5. Mark account complete for this business date
        LocalDate today = LocalDate.now();
        audit.markAccountComplete(accountId, snapshot.clientId(), today);

        // 6. Publish position change event
        publishPositionChange("EOD_COMPLETE", snapshot);

        // 7. Check if all accounts for client are complete
        if (audit.isClientComplete(snapshot.clientId(), today)) {
            publishClientSignOff(snapshot.clientId(), today);
        }
    }

    // ==================== FLOW 2: INTRADAY PROCESSING ====================

    /**
     * Process a single intraday record from MSPA Kafka batch.
     * Called for each record in the batch (1-100 records per batch).
     */
    public void processIntradayRecord(String recordValue) {
        try {
            AccountSnapshotDTO snapshot = json.readValue(recordValue, AccountSnapshotDTO.class);
            processIntraday(snapshot);
        } catch (Exception e) {
            log.error("Failed to parse intraday record: {}", e.getMessage());
            throw new RuntimeException("Invalid intraday record", e);
        }
    }

    /**
     * Process intraday position update.
     * Unlike EOD, this does NOT create a new batch - just updates current positions.
     */
    @Transactional
    public void processIntraday(AccountSnapshotDTO snapshot) {
        Integer accountId = snapshot.accountId();
        log.debug("Processing intraday: account={}", accountId);

        List<PositionDTO> positionList = sanitize(snapshot.positions());
        if (positionList == null || positionList.isEmpty()) {
            log.debug("No positions in intraday update: account={}", accountId);
            return;
        }

        // Ensure reference data exists
        refData.ensureReferenceData(snapshot);

        // Update positions (bitemporal: close old version, insert new)
        for (PositionDTO pos : positionList) {
            positions.updatePosition(accountId, pos);
        }

        // Publish change event
        publishPositionChange("INTRADAY_UPDATE", snapshot);

        log.debug("Intraday complete: account={}, positions={}", accountId, positionList.size());
    }

    // ==================== FLOW 4: MANUAL UPLOAD ====================

    /**
     * Process manual position upload from FXAN UI.
     * Used for non-FS accounts that are not in MSPA.
     */
    @Transactional
    public void processManualUpload(AccountSnapshotDTO snapshot, String uploadedBy) {
        Integer accountId = snapshot.accountId();
        log.info("Processing manual upload: account={}, user={}", accountId, uploadedBy);

        // Validate
        ValidationResult result = validation.validate(snapshot);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("Validation failed: " + result.errorSummary());
        }

        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // Ensure reference data
        refData.ensureReferenceData(snapshot);

        // Create new batch for upload
        int batchId = positions.createBatch(accountId);

        // Insert positions
        if (positionList != null && !positionList.isEmpty()) {
            positions.insertPositions(accountId, positionList, "MANUAL_UPLOAD", batchId);
            transactions.insertTransactions(accountId, positionList);
        }

        // Activate batch
        positions.activateBatch(accountId, batchId);
        positions.cleanupBatches(accountId);

        // Audit log
        audit.log("MANUAL_UPLOAD", accountId.toString(), uploadedBy, "Uploaded " + (positionList != null ? positionList.size() : 0) + " positions");

        // Publish change event
        publishPositionChange("MANUAL_UPLOAD", snapshot);

        log.info("Manual upload complete: account={}, positions={}", accountId, positionList != null ? positionList.size() : 0);
    }

    // ==================== HELPERS ====================

    private List<PositionDTO> sanitize(List<PositionDTO> list) {
        if (list == null) return null;
        return list.stream().filter(p -> p != null && p.productId() != null).toList();
    }

    private void publishPositionChange(String eventType, AccountSnapshotDTO snapshot) {
        Events.PositionChange event = new Events.PositionChange(eventType, snapshot.accountId(), snapshot.clientId(), snapshot.positionCount());
        kafka.send(KafkaConfig.TOPIC_POSITION_CHANGES, snapshot.accountId().toString(), event);
        log.debug("Published {} event: account={}", eventType, snapshot.accountId());
    }

    private void publishClientSignOff(Integer clientId, LocalDate date) {
        int totalAccounts = audit.countClientAccounts(clientId);

        Events.ClientSignOff event = new Events.ClientSignOff(clientId, date, totalAccounts);
        kafka.send(KafkaConfig.TOPIC_SIGNOFF, clientId.toString(), event);

        log.info("Client sign-off published: clientId={}, date={}, accounts={}", clientId, date, totalAccounts);
    }
}