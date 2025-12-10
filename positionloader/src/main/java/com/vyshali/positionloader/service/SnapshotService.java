package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - FIXED: Corrected class references and method signatures
 *
 * Changes:
 * - MspmClient -> MspmService (actual class name)
 * - PositionDto -> PositionDTO (actual class name)
 * - Removed non-existent MspmPositionResponse
 * - Added missing methods referenced by tests and controllers
 *
 * @author Vyshali Prabananth Lal
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.Events;
import com.vyshali.positionloader.dto.PositionDTO;
import com.vyshali.positionloader.event.DomainEvents.*;
import com.vyshali.positionloader.repository.AuditRepository;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmService mspmService;
    private final PositionRepository positions;
    private final ReferenceDataRepository refData;
    private final AuditRepository audit;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    // ==================== EOD PROCESSING ====================

    /**
     * Process EOD for a single account.
     * Called by KafkaListeners and OpsController.
     */
    @Transactional
    public void processEod(Integer accountId) {
        long startTime = System.currentTimeMillis();
        log.info("Starting EOD for account {}", accountId);

        // 1. Fetch from MSPM
        AccountSnapshotDTO snapshot = mspmService.fetchSnapshot(accountId);
        if (snapshot == null) {
            throw new RuntimeException("MSPM returned null for account " + accountId);
        }

        // 2. Ensure reference data exists
        refData.ensureReferenceData(snapshot);

        // 3. Create new batch
        int batchId = positions.createBatch(accountId);

        // 4. Filter and insert positions
        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            List<PositionDTO> validPositions = filterValidPositions(snapshot.positions(), accountId);
            positions.insertPositions(accountId, validPositions, "MSPM_EOD", batchId);
        }

        // 5. Activate batch (atomic swap)
        positions.activateBatch(accountId, batchId);

        // 6. Mark account complete
        audit.markAccountComplete(accountId, snapshot.clientId(), LocalDate.now());

        // 7. Publish events
        long duration = System.currentTimeMillis() - startTime;
        publishPositionChangeEvent(accountId, snapshot.clientId(), "EOD_COMPLETE", snapshot.positionCount());

        // 8. Check for client sign-off
        checkAndPublishClientSignOff(snapshot.clientId());

        log.info("EOD complete for account {} in {}ms", accountId, duration);
    }

    // ==================== INTRADAY PROCESSING ====================

    /**
     * Process intraday update from AccountSnapshotDTO.
     * Called by REST endpoint.
     */
    @Transactional
    public void processIntraday(AccountSnapshotDTO snapshot) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot");
        }

        log.debug("Processing intraday for account {}", snapshot.accountId());

        // Ensure ref data
        refData.ensureReferenceData(snapshot);

        // Update each position
        if (snapshot.positions() != null) {
            for (PositionDTO pos : snapshot.positions()) {
                if (pos != null) {
                    positions.updatePosition(snapshot.accountId(), pos);
                }
            }

            // Publish event
            publishPositionChangeEvent(snapshot.accountId(), snapshot.clientId(), "INTRADAY", snapshot.positionCount());
        }
    }

    /**
     * Process intraday from JSON string.
     * Called by Kafka listener.
     */
    public void processIntradayRecord(String jsonRecord) {
        try {
            AccountSnapshotDTO snapshot = json.readValue(jsonRecord, AccountSnapshotDTO.class);
            processIntraday(snapshot);
        } catch (Exception e) {
            log.error("Failed to parse intraday record: {}", e.getMessage());
            throw new RuntimeException("Invalid intraday record: " + e.getMessage(), e);
        }
    }

    // ==================== MANUAL UPLOAD ====================

    /**
     * Process manual position upload.
     * Called by UploadController.
     */
    @Transactional
    public void processManualUpload(AccountSnapshotDTO snapshot, String user) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        log.info("Processing manual upload for account {} by user {}", snapshot.accountId(), user);

        // Ensure ref data
        refData.ensureReferenceData(snapshot);

        // Create new batch
        int batchId = positions.createBatch(snapshot.accountId());

        // Insert positions
        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            positions.insertPositions(snapshot.accountId(), snapshot.positions(), "MANUAL_UPLOAD", batchId);
        }

        // Activate batch
        positions.activateBatch(snapshot.accountId(), batchId);

        // Audit log
        audit.log("MANUAL_UPLOAD", snapshot.accountId().toString(), user, String.format("Uploaded %d positions", snapshot.positionCount()));

        // Publish event
        publishPositionChangeEvent(snapshot.accountId(), snapshot.clientId(), "MANUAL_UPLOAD", snapshot.positionCount());
    }

    // ==================== HELPER METHODS ====================

    /**
     * Filter valid positions (remove nulls, log zero-price warnings).
     */
    private List<PositionDTO> filterValidPositions(List<PositionDTO> positions, Integer accountId) {
        List<PositionDTO> valid = new ArrayList<>();
        int zeroPriceCount = 0;

        for (PositionDTO pos : positions) {
            if (pos == null) continue;

            if (pos.hasZeroPrice()) {
                zeroPriceCount++;
                log.warn("Zero price for product {} in account {}", pos.productId(), accountId);
            }
            valid.add(pos);
        }

        if (zeroPriceCount > 0) {
            log.warn("Account {} has {} zero-price positions", accountId, zeroPriceCount);
        }

        return valid;
    }

    /**
     * Publish position change event to Kafka.
     */
    private void publishPositionChangeEvent(Integer accountId, Integer clientId, String eventType, int positionCount) {
        Events.PositionChange event = new Events.PositionChange(eventType, accountId, clientId, positionCount);
        kafka.send("POSITION_CHANGE_EVENTS", accountId.toString(), event);
    }

    /**
     * Check if all accounts for client are complete and publish sign-off.
     */
    private void checkAndPublishClientSignOff(Integer clientId) {
        if (clientId == null) return;

        if (audit.isClientComplete(clientId, LocalDate.now())) {
            int accountCount = audit.countClientAccounts(clientId);
            Events.ClientSignOff signOff = new Events.ClientSignOff(clientId, LocalDate.now(), accountCount);
            kafka.send("CLIENT_REPORTING_SIGNOFF", clientId.toString(), signOff);
            log.info("Client {} sign-off published ({} accounts)", clientId, accountCount);
        }
    }
}