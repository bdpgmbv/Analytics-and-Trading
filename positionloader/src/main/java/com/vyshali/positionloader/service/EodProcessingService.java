package com.vyshali.positionloader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.entity.Position;
import com.vyshali.common.repository.AuditRepository;
import com.vyshali.common.repository.PositionRepository;
import com.vyshali.common.repository.ReferenceDataRepository;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for End-of-Day (EOD) position processing.
 * Handles batch position loading from MSPM and persistence to FXAN database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodProcessingService {

    private final PositionRepository positionRepository;
    private final ReferenceDataRepository referenceDataRepository;
    private final AuditRepository auditRepository;
    private final MspmClientService mspmClientService;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ═══════════════════════════════════════════════════════════════════════════
    // FULL EOD PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process full EOD for all active accounts.
     * @param businessDate The business date to process
     */
    @Transactional
    public EodProcessingResult processFullEod(LocalDate businessDate) {
        log.info("Starting full EOD processing for {}", businessDate);
        Timer.Sample sample = Timer.start(meterRegistry);

        EodProcessingResult result = new EodProcessingResult(businessDate);

        try {
            // Get all active account IDs
            List<Integer> activeAccounts = referenceDataRepository.getAllActiveAccountIds();
            log.info("Processing EOD for {} active accounts", activeAccounts.size());

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Process in batches based on config
            int batchSize = config.batch().size();
            for (int i = 0; i < activeAccounts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, activeAccounts.size());
                List<Integer> batch = activeAccounts.subList(i, end);

                for (Integer accountId : batch) {
                    try {
                        processAccountEod(accountId, businessDate);
                        successCount.incrementAndGet();
                        result.addSuccessfulAccount(accountId);
                    } catch (Exception e) {
                        log.error("Failed to process EOD for account {}: {}", accountId, e.getMessage());
                        failCount.incrementAndGet();
                        result.addFailedAccount(accountId, e.getMessage());
                        alertService.eodFailed(accountId, e.getMessage());
                    }
                }
            }

            result.setSuccess(failCount.get() == 0);

            // Log summary
            log.info("EOD processing complete: success={}, failed={}", successCount.get(), failCount.get());

            // Record audit
            recordAudit(businessDate, "FULL_EOD", successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("Full EOD processing failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            alertService.eodFailed(businessDate, e.getMessage());
        } finally {
            sample.stop(Timer.builder("eod.processing.full")
                    .tag("businessDate", businessDate.toString())
                    .register(meterRegistry));
        }

        return result;
    }

    /**
     * Process EOD for a specific account.
     * @param accountId Account to process
     * @param businessDate Business date
     */
    @Transactional
    public void processAccountEod(int accountId, LocalDate businessDate) {
        log.debug("Processing EOD for account {} on {}", accountId, businessDate);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Check pilot mode
            if (!config.features().shouldProcessAccount(accountId)) {
                log.debug("Skipping account {} - not in pilot mode", accountId);
                return;
            }

            // Delete existing positions for this account/date
            int deleted = deleteExistingPositions(accountId, businessDate);
            log.debug("Deleted {} existing positions for account {}", deleted, accountId);

            // Fetch positions from MSPM
            List<PositionDto> mspmPositions = mspmClientService.getEodPositions(accountId, businessDate);
            log.debug("Fetched {} positions from MSPM for account {}", mspmPositions.size(), accountId);

            // Save to database
            List<Position> savedPositions = savePositions(mspmPositions, accountId, businessDate);

            // Publish change events
            publishPositionChanges(savedPositions, "EOD_LOAD");

            meterRegistry.counter("eod.accounts.processed", "status", "success").increment();
            meterRegistry.counter("eod.positions.loaded").increment(savedPositions.size());

        } catch (Exception e) {
            log.error("EOD processing failed for account {}: {}", accountId, e.getMessage(), e);
            meterRegistry.counter("eod.accounts.processed", "status", "failed").increment();
            throw e;
        } finally {
            sample.stop(Timer.builder("eod.processing.account")
                    .tag("accountId", String.valueOf(accountId))
                    .register(meterRegistry));
        }
    }

    /**
     * Async EOD processing for parallel execution.
     */
    @Async
    public CompletableFuture<Void> processAccountEodAsync(int accountId, LocalDate businessDate) {
        processAccountEod(accountId, businessDate);
        return CompletableFuture.completedFuture(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process an EOD batch message from Kafka.
     */
    @Transactional
    public void processEodBatch(PositionDto.EodBatch batch) {
        log.info("Processing EOD batch: accountId={}, positions={}, businessDate={}",
                batch.accountId(), batch.positions().size(), batch.businessDate());

        int accountId = batch.accountId();
        LocalDate businessDate = batch.businessDate();

        try {
            // Delete existing positions
            deleteExistingPositions(accountId, businessDate);

            // Save new positions
            List<Position> saved = savePositions(batch.positions(), accountId, businessDate);

            // Publish changes
            publishPositionChanges(saved, "EOD_BATCH");

            meterRegistry.counter("eod.batch.processed", "status", "success").increment();

        } catch (Exception e) {
            log.error("EOD batch processing failed: accountId={}, error={}", accountId, e.getMessage(), e);
            alertService.eodFailed(accountId, e.getMessage());
            meterRegistry.counter("eod.batch.processed", "status", "failed").increment();
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Delete existing positions for account/date.
     */
    private int deleteExistingPositions(int accountId, LocalDate businessDate) {
        List<Position> existing = positionRepository.findByAccountIdAndBusinessDate(accountId, businessDate);
        positionRepository.deleteAll(existing);
        return existing.size();
    }

    /**
     * Save positions to database.
     */
    private List<Position> savePositions(List<PositionDto> dtos, int accountId, LocalDate businessDate) {
        List<Position> positions = new ArrayList<>();

        for (PositionDto dto : dtos) {
            Position position = new Position();
            position.setAccountId(accountId);
            position.setSecurityId(dto.securityId());
            position.setBusinessDate(businessDate);
            position.setQuantity(dto.quantity());
            position.setMarketValue(dto.marketValue());
            position.setCostBasis(dto.costBasis());
            position.setCurrency(dto.currency());
            position.setCreatedAt(LocalDateTime.now());
            position.setLastUpdated(LocalDateTime.now());

            positions.add(position);
        }

        return positionRepository.saveAll(positions);
    }

    /**
     * Publish position change events to Kafka.
     */
    private void publishPositionChanges(List<Position> positions, String changeType) {
        for (Position position : positions) {
            try {
                PositionDto.PositionChangeEvent event = new PositionDto.PositionChangeEvent(
                        position.getAccountId(),
                        position.getSecurityId(),
                        position.getBusinessDate(),
                        changeType,
                        position.getQuantity(),
                        position.getMarketValue(),
                        LocalDateTime.now()
                );

                String json = objectMapper.writeValueAsString(event);
                String key = position.getAccountId() + "-" + position.getSecurityId();

                kafkaTemplate.send(config.kafka().positionChanges(), key, json);

            } catch (Exception e) {
                log.error("Failed to publish position change event: {}", e.getMessage());
            }
        }
    }

    /**
     * Record audit entry for EOD processing.
     */
    private void recordAudit(LocalDate businessDate, String operation, int successCount, int failCount) {
        try {
            auditRepository.recordEodAudit(
                    businessDate,
                    operation,
                    successCount,
                    failCount,
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Failed to record audit: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT DTO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of EOD processing run.
     */
    public static class EodProcessingResult {
        private final LocalDate businessDate;
        private final List<Integer> successfulAccounts = new ArrayList<>();
        private final List<AccountFailure> failedAccounts = new ArrayList<>();
        private boolean success;
        private String errorMessage;

        public EodProcessingResult(LocalDate businessDate) {
            this.businessDate = businessDate;
        }

        public void addSuccessfulAccount(int accountId) {
            successfulAccounts.add(accountId);
        }

        public void addFailedAccount(int accountId, String error) {
            failedAccounts.add(new AccountFailure(accountId, error));
        }

        public LocalDate getBusinessDate() {
            return businessDate;
        }

        public List<Integer> getSuccessfulAccounts() {
            return successfulAccounts;
        }

        public List<AccountFailure> getFailedAccounts() {
            return failedAccounts;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public record AccountFailure(int accountId, String error) {}
    }
}