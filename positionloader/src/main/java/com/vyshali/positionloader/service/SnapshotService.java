package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - MERGED: SnapshotService now includes EodService functionality
 *
 * BEFORE:
 * - EodService: thin wrapper calling SnapshotService
 * - SnapshotService: actual EOD logic
 * - Unnecessary indirection
 *
 * AFTER:
 * - Single SnapshotService handles all EOD and Intraday processing
 * - Domain events for decoupling
 * - Cleaner API
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.TracingConfig.SpanCreator;
import com.vyshali.positionloader.dto.MspmPositionResponse;
import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.event.DomainEvents.*;
import com.vyshali.positionloader.repository.PositionRepository;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final MspmClient mspmClient;
    private final PositionRepository positions;
    private final ReferenceDataRepository refData;
    private final IdempotencyService idempotency;
    private final MetricsService metrics;
    private final ApplicationEventPublisher events;
    private final SpanCreator spanCreator;

    @Value("${features.caching.enabled:true}")
    private boolean cachingEnabled;

    @Value("${features.idempotency.enabled:true}")
    private boolean idempotencyEnabled;

    @Value("${features.zero-price-filter.enabled:true}")
    private boolean zeroPriceFilterEnabled;

    // ==================== EOD PROCESSING (formerly in EodService) ====================

    /**
     * Process EOD for a single account.
     * This is the main entry point for EOD processing.
     */
    @Observed(name = "eod.account.process", contextualName = "process-eod-account")
    @Transactional
    public EodResult processEodForAccount(Integer accountId, Integer clientId, LocalDate businessDate) {
        long startTime = System.currentTimeMillis();
        log.info("Starting EOD: account={}, date={}", accountId, businessDate);

        // Publish start event
        events.publishEvent(new EodStarted(accountId, clientId, businessDate));

        try (var span = spanCreator.startSpan("eod-account", "accountId", accountId.toString())) {
            // 1. Fetch positions from MSPM
            span.event("fetching-mspm");
            List<MspmPositionResponse> mspmPositions = mspmClient.fetchPositions(accountId, businessDate);

            if (mspmPositions.isEmpty()) {
                log.warn("No positions from MSPM: account={}", accountId);
                return EodResult.empty(accountId, businessDate);
            }

            // 2. Transform and validate
            span.event("transforming");
            List<PositionDto> validated = transformAndValidate(mspmPositions, accountId, clientId);

            // 3. Save snapshot
            span.event("saving-snapshot");
            int savedCount = saveEodSnapshot(accountId, clientId, validated, businessDate);

            long durationMs = System.currentTimeMillis() - startTime;

            // 4. Publish completion event
            events.publishEvent(new EodCompleted(accountId, clientId, businessDate, savedCount, durationMs));

            log.info("EOD complete: account={}, positions={}, duration={}ms", accountId, savedCount, durationMs);

            return new EodResult(accountId, businessDate, savedCount, durationMs, true, null);

        } catch (Exception e) {
            log.error("EOD failed: account={}, error={}", accountId, e.getMessage(), e);

            // Publish failure event
            events.publishEvent(new EodFailed(accountId, clientId, businessDate, e.getMessage(), e.getClass().getSimpleName()));

            return new EodResult(accountId, businessDate, 0, System.currentTimeMillis() - startTime, false, e.getMessage());
        }
    }

    /**
     * Process EOD for all accounts of a client.
     */
    @Observed(name = "eod.client.process", contextualName = "process-eod-client")
    public ClientEodResult processEodForClient(Integer clientId, LocalDate businessDate) {
        long startTime = System.currentTimeMillis();
        log.info("Starting client EOD: client={}, date={}", clientId, businessDate);

        // Get all accounts for client
        List<Integer> accountIds = refData.getAccountsForClient(clientId);
        if (accountIds.isEmpty()) {
            log.warn("No accounts found for client: {}", clientId);
            return new ClientEodResult(clientId, businessDate, 0, 0, 0, List.of());
        }

        List<EodResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Integer accountId : accountIds) {
            EodResult result = processEodForAccount(accountId, clientId, businessDate);
            results.add(result);
            if (result.success()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Publish client sign-off if all successful
        if (failCount == 0) {
            events.publishEvent(new ClientSignOffCompleted(clientId, businessDate, accountIds.size(), durationMs));
        }

        log.info("Client EOD complete: client={}, success={}, failed={}, duration={}ms", clientId, successCount, failCount, durationMs);

        return new ClientEodResult(clientId, businessDate, successCount, failCount, durationMs, results);
    }

    /**
     * Check if client EOD is complete (all accounts processed).
     */
    public boolean isClientEodComplete(Integer clientId, LocalDate businessDate) {
        return refData.areAllAccountsComplete(clientId, businessDate);
    }

    // ==================== INTRADAY PROCESSING ====================

    /**
     * Process intraday position update.
     */
    @Observed(name = "intraday.process", contextualName = "process-intraday")
    @Transactional
    public int processIntraday(Integer accountId, Integer clientId, List<PositionDto> incomingPositions) {
        log.debug("Processing intraday: account={}, count={}", accountId, incomingPositions.size());

        // Filter duplicates using idempotency service
        List<PositionDto> deduplicated = filterDuplicates(incomingPositions);
        if (deduplicated.isEmpty()) {
            log.debug("All positions were duplicates: account={}", accountId);
            return 0;
        }

        // Apply zero-price filter
        List<PositionDto> validated = filterZeroPrices(deduplicated, accountId);

        // Batch update
        int updatedCount = positions.updatePositions(validated);

        // Mark as processed for idempotency
        markProcessed(validated);

        // Publish event
        int batchId = positions.getActiveBatchId(accountId);
        events.publishEvent(new PositionsUpdated(accountId, clientId, "INTRADAY", updatedCount, batchId));

        metrics.recordIntradayUpdate(updatedCount);
        return updatedCount;
    }

    // ==================== SNAPSHOT OPERATIONS ====================

    /**
     * Save EOD snapshot with batch swap.
     */
    @Transactional
    public int saveEodSnapshot(Integer accountId, Integer clientId, List<PositionDto> positionDtos, LocalDate businessDate) {
        log.debug("Saving EOD snapshot: account={}, positions={}", accountId, positionDtos.size());

        // Get current and next batch IDs
        int currentBatch = positions.getActiveBatchId(accountId);
        int nextBatch = (currentBatch == 1) ? 2 : 1;

        // Clear next batch and insert new positions
        positions.clearBatch(accountId, nextBatch);
        int insertedCount = positions.insertPositions(positionDtos, nextBatch, businessDate);

        // Swap active batch
        positions.setActiveBatch(accountId, nextBatch);

        // Publish batch swap event
        events.publishEvent(new BatchSwapped(accountId, currentBatch, nextBatch));

        // Mark all as processed
        markProcessed(positionDtos);

        metrics.recordEodSnapshot(insertedCount);
        return insertedCount;
    }

    // ==================== VALIDATION & TRANSFORMATION ====================

    /**
     * Transform MSPM response to DTOs and validate.
     */
    private List<PositionDto> transformAndValidate(List<MspmPositionResponse> mspmPositions, Integer accountId, Integer clientId) {
        List<PositionDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int duplicateCount = 0;
        int zeroPriceCount = 0;

        for (MspmPositionResponse mspm : mspmPositions) {
            // Dedupe by external ref
            String key = mspm.getExternalRefId();
            if (key != null && !seen.add(key)) {
                duplicateCount++;
                continue;
            }

            // Zero price filter
            if (zeroPriceFilterEnabled && isZeroPrice(mspm.getPrice())) {
                zeroPriceCount++;
                continue;
            }

            // Transform
            PositionDto dto = PositionDto.builder().accountId(accountId).clientId(clientId).productId(mspm.getProductId()).quantity(mspm.getQuantity()).price(mspm.getPrice()).marketValue(calculateMarketValue(mspm.getQuantity(), mspm.getPrice())).externalRefId(mspm.getExternalRefId()).currency(mspm.getCurrency()).build();

            result.add(dto);
        }

        // Publish validation issues if any
        if (duplicateCount > 0) {
            events.publishEvent(new ValidationIssueDetected(accountId, "DUPLICATE", duplicateCount, List.of()));
        }
        if (zeroPriceCount > 0) {
            events.publishEvent(new ValidationIssueDetected(accountId, "ZERO_PRICE", zeroPriceCount, List.of()));
        }

        log.debug("Validated: account={}, valid={}, duplicates={}, zeroPrices={}", accountId, result.size(), duplicateCount, zeroPriceCount);

        return result;
    }

    /**
     * Filter duplicates using idempotency service.
     */
    private List<PositionDto> filterDuplicates(List<PositionDto> positions) {
        if (!idempotencyEnabled) {
            return positions;
        }

        return positions.stream().filter(p -> p.getExternalRefId() == null || !idempotency.isDuplicate(p.getExternalRefId())).toList();
    }

    /**
     * Filter zero-price positions.
     */
    private List<PositionDto> filterZeroPrices(List<PositionDto> positions, Integer accountId) {
        if (!zeroPriceFilterEnabled) {
            return positions;
        }

        List<PositionDto> valid = new ArrayList<>();
        int filtered = 0;

        for (PositionDto p : positions) {
            if (isZeroPrice(p.getPrice())) {
                filtered++;
            } else {
                valid.add(p);
            }
        }

        if (filtered > 0) {
            metrics.recordZeroPriceDetected(filtered);
            events.publishEvent(new ValidationIssueDetected(accountId, "ZERO_PRICE", filtered, List.of()));
        }

        return valid;
    }

    /**
     * Mark positions as processed in idempotency service.
     */
    private void markProcessed(List<PositionDto> positions) {
        if (!idempotencyEnabled) return;

        List<String> refIds = positions.stream().map(PositionDto::getExternalRefId).filter(Objects::nonNull).toList();

        idempotency.markProcessedBatch(refIds);
    }

    private boolean isZeroPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) == 0;
    }

    private BigDecimal calculateMarketValue(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null) return BigDecimal.ZERO;
        return quantity.multiply(price);
    }

    // ==================== RESULT DTOs ====================

    public record EodResult(Integer accountId, LocalDate businessDate, int positionCount, long durationMs,
                            boolean success, String errorMessage) {
        public static EodResult empty(Integer accountId, LocalDate date) {
            return new EodResult(accountId, date, 0, 0, true, null);
        }
    }

    public record ClientEodResult(Integer clientId, LocalDate businessDate, int successCount, int failCount,
                                  long durationMs, List<EodResult> accountResults) {
        public boolean allSuccessful() {
            return failCount == 0;
        }
    }
}