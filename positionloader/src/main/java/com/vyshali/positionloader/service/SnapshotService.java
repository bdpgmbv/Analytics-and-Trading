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
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
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
 * Three flows:
 * 1. EOD Load: Full refresh from MSPM after market close
 * 2. Intraday: Incremental updates from MSPA
 * 3. Manual Upload: Position uploads from UI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmService mspm;
    private final PositionRepository positions;
    private final ReferenceDataRepository refData;
    private final AuditRepository audit;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;

    // ==================== FLOW 1: EOD ====================

    /**
     * Process EOD for an account.
     * Called when MSPM sends trigger after market close.
     */
    public void processEod(Integer accountId) {
        log.info("Starting EOD: account={}", accountId);

        // Fetch from MSPM
        AccountSnapshotDTO snapshot = mspm.fetchSnapshot(accountId);
        if (snapshot == null) {
            throw new RuntimeException("MSPM returned null for account " + accountId);
        }

        // Validate
        validateSnapshot(snapshot);

        // Save
        saveEodSnapshot(snapshot);

        log.info("EOD complete: account={}, positions={}", accountId, snapshot.positionCount());
    }

    @Transactional
    public void saveEodSnapshot(AccountSnapshotDTO snapshot) {
        Integer accountId = snapshot.accountId();
        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // 1. Ensure reference data exists
        refData.ensureReferenceData(snapshot);

        // 2. Create new batch
        int batchId = positions.createBatch(accountId);

        // 3. Insert positions
        if (!positionList.isEmpty()) {
            positions.insertPositions(accountId, positionList, "MSPM_EOD", batchId);
        }

        // 4. Activate batch (atomic swap)
        positions.activateBatch(accountId, batchId);

        // 5. Mark complete
        LocalDate today = LocalDate.now();
        audit.markAccountComplete(accountId, snapshot.clientId(), today);

        // 6. Publish event
        publishPositionChange("EOD_COMPLETE", snapshot);

        // 7. Check client complete
        if (audit.isClientComplete(snapshot.clientId(), today)) {
            publishClientSignOff(snapshot.clientId(), today);
        }
    }

    // ==================== FLOW 2: INTRADAY ====================

    /**
     * Process intraday record from Kafka.
     */
    public void processIntradayRecord(String jsonRecord) {
        try {
            AccountSnapshotDTO snapshot = json.readValue(jsonRecord, AccountSnapshotDTO.class);
            processIntraday(snapshot);
        } catch (Exception e) {
            log.error("Failed to parse intraday record: {}", e.getMessage());
            throw new RuntimeException("Invalid intraday record", e);
        }
    }

    @Transactional
    public void processIntraday(AccountSnapshotDTO snapshot) {
        Integer accountId = snapshot.accountId();
        List<PositionDTO> positionList = sanitize(snapshot.positions());

        if (positionList.isEmpty()) {
            return;
        }

        // Ensure reference data
        refData.ensureReferenceData(snapshot);

        // Update positions
        for (PositionDTO pos : positionList) {
            positions.updatePosition(accountId, pos);
        }

        // Publish event
        publishPositionChange("INTRADAY_UPDATE", snapshot);

        log.debug("Intraday complete: account={}, positions={}", accountId, positionList.size());
    }

    // ==================== FLOW 3: MANUAL UPLOAD ====================

    @Transactional
    public void processManualUpload(AccountSnapshotDTO snapshot, String uploadedBy) {
        Integer accountId = snapshot.accountId();

        // Validate
        validateSnapshot(snapshot);

        List<PositionDTO> positionList = sanitize(snapshot.positions());

        // Ensure reference data
        refData.ensureReferenceData(snapshot);

        // Create batch
        int batchId = positions.createBatch(accountId);

        // Insert positions
        if (!positionList.isEmpty()) {
            positions.insertPositions(accountId, positionList, "MANUAL_UPLOAD", batchId);
        }

        // Activate batch
        positions.activateBatch(accountId, batchId);

        // Audit
        audit.log("MANUAL_UPLOAD", accountId.toString(), uploadedBy, "Uploaded " + positionList.size() + " positions");

        // Publish event
        publishPositionChange("MANUAL_UPLOAD", snapshot);

        log.info("Manual upload complete: account={}, positions={}", accountId, positionList.size());
    }

    // ==================== HELPERS ====================

    private void validateSnapshot(AccountSnapshotDTO snapshot) {
        if (snapshot.accountId() == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (snapshot.positions() != null) {
            long zeroPriceCount = snapshot.positions().stream().filter(PositionDTO::hasZeroPrice).count();

            if (zeroPriceCount > 0) {
                log.warn("Account {} has {} zero-price positions", snapshot.accountId(), zeroPriceCount);
            }
        }
    }

    private List<PositionDTO> sanitize(List<PositionDTO> list) {
        if (list == null) return List.of();
        return list.stream().filter(p -> p != null && p.productId() != null).toList();
    }

    private void publishPositionChange(String eventType, AccountSnapshotDTO snapshot) {
        var event = new Events.PositionChange(eventType, snapshot.accountId(), snapshot.clientId(), snapshot.positionCount());
        kafka.send(KafkaConfig.TOPIC_POSITION_CHANGES, snapshot.accountId().toString(), event);
    }

    private void publishClientSignOff(Integer clientId, LocalDate date) {
        int totalAccounts = audit.countClientAccounts(clientId);
        var event = new Events.ClientSignOff(clientId, date, totalAccounts);
        kafka.send(KafkaConfig.TOPIC_SIGNOFF, clientId.toString(), event);
        log.info("Client sign-off published: clientId={}", clientId);
    }
}