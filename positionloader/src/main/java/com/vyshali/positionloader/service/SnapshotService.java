package com.vyshali.positionloader.service;

/*
 * SIMPLIFIED VERSION WITH METRICS
 *
 * - Removed: ApplicationEventPublisher, DomainEvents
 * - Added: Direct MetricsService calls (previously in DomainEventListeners)
 */

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

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
    private final MetricsService metrics;  // ADD THIS - for recording metrics directly

    // ==================== EOD PROCESSING ====================

    @Transactional
    public void processEod(Integer accountId) {
        long startTime = System.currentTimeMillis();
        LocalDate businessDate = LocalDate.now();

        log.info("Starting EOD for account {}", accountId);

        try {
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
            int positionCount = 0;
            if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
                List<PositionDTO> validPositions = filterValidPositions(snapshot.positions(), accountId);
                positions.insertPositions(accountId, validPositions, "MSPM_EOD", batchId);
                positionCount = validPositions.size();
            }

            // 5. Activate batch (atomic swap)
            positions.activateBatch(accountId, batchId);

            // 6. Mark account complete
            audit.markAccountComplete(accountId, snapshot.clientId(), businessDate);

            // 7. Publish to Kafka
            publishPositionChangeEvent(accountId, snapshot.clientId(), "EOD_COMPLETE", positionCount);

            // 8. Check for client sign-off
            checkAndPublishClientSignOff(snapshot.clientId(), businessDate);

            // 9. Record success metric (WAS IN DomainEventListeners, NOW HERE)
            metrics.recordAccountSuccess();
            metrics.recordEodSnapshot(positionCount);

            long duration = System.currentTimeMillis() - startTime;
            log.info("EOD complete for account {} in {}ms ({} positions)", accountId, duration, positionCount);

        } catch (Exception e) {
            // Record failure metric (WAS IN DomainEventListeners, NOW HERE)
            metrics.recordAccountFailure();
            audit.log("EOD_FAILED", accountId.toString(), "SYSTEM", "Error: " + e.getMessage());

            log.error("EOD failed for account {}: {}", accountId, e.getMessage());
            throw e;
        }
    }

    // ==================== INTRADAY PROCESSING ====================

    @Transactional
    public void processIntraday(AccountSnapshotDTO snapshot) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Invalid snapshot");
        }

        log.debug("Processing intraday for account {}", snapshot.accountId());

        refData.ensureReferenceData(snapshot);

        if (snapshot.positions() != null) {
            for (PositionDTO pos : snapshot.positions()) {
                if (pos != null) {
                    positions.updatePosition(snapshot.accountId(), pos);
                }
            }

            publishPositionChangeEvent(snapshot.accountId(), snapshot.clientId(), "INTRADAY", snapshot.positionCount());

            // Record intraday metric (WAS IN DomainEventListeners, NOW HERE)
            metrics.recordIntradayUpdate(snapshot.positionCount());
        }
    }

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

    @Transactional
    public void processManualUpload(AccountSnapshotDTO snapshot, String user) {
        if (snapshot == null || snapshot.accountId() == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        log.info("Processing manual upload for account {} by user {}", snapshot.accountId(), user);

        refData.ensureReferenceData(snapshot);

        int batchId = positions.createBatch(snapshot.accountId());

        if (snapshot.positions() != null && !snapshot.positions().isEmpty()) {
            positions.insertPositions(snapshot.accountId(), snapshot.positions(), "MANUAL_UPLOAD", batchId);
        }

        positions.activateBatch(snapshot.accountId(), batchId);

        audit.log("MANUAL_UPLOAD", snapshot.accountId().toString(), user, String.format("Uploaded %d positions", snapshot.positionCount()));

        publishPositionChangeEvent(snapshot.accountId(), snapshot.clientId(), "MANUAL_UPLOAD", snapshot.positionCount());
    }

    // ==================== HELPER METHODS ====================

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
            // Record zero price metric (WAS IN DomainEventListeners, NOW HERE)
            metrics.recordZeroPriceDetected(zeroPriceCount);
        }

        return valid;
    }

    private void publishPositionChangeEvent(Integer accountId, Integer clientId, String eventType, int positionCount) {
        Events.PositionChange event = new Events.PositionChange(eventType, accountId, clientId, positionCount);
        kafka.send("POSITION_CHANGE_EVENTS", accountId.toString(), event);
        log.debug("Published {} event for account {}", eventType, accountId);
    }

    private void checkAndPublishClientSignOff(Integer clientId, LocalDate businessDate) {
        if (clientId == null) return;

        if (audit.isClientComplete(clientId, businessDate)) {
            int accountCount = audit.countClientAccounts(clientId);

            Events.ClientSignOff signOff = new Events.ClientSignOff(clientId, businessDate, accountCount);
            kafka.send("CLIENT_REPORTING_SIGNOFF", clientId.toString(), signOff);

            log.info("Client {} sign-off published ({} accounts)", clientId, accountCount);
        }
    }
}