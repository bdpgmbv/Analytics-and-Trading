package com.vyshali.positionloader.service;

import com.vyshali.common.dto.PositionDto;
import com.vyshali.common.repository.ReferenceDataRepository;
import com.vyshali.common.service.AlertService;
import com.vyshali.positionloader.config.LoaderConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level EOD (End-of-Day) orchestration service.
 * Coordinates EOD processing across accounts with parallel execution support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodService {

    private final EodProcessingService eodProcessingService;
    private final ReferenceDataRepository referenceDataRepository;
    private final LoaderConfig config;
    private final AlertService alertService;
    private final MeterRegistry meterRegistry;

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process full EOD for all active accounts.
     * @param businessDate Business date to process
     * @return Processing result
     */
    public EodResult processFullEod(LocalDate businessDate) {
        log.info("Starting full EOD processing for {}", businessDate);
        Timer.Sample sample = Timer.start(meterRegistry);

        EodResult result = new EodResult(businessDate);

        try {
            // Get all active accounts
            List<Integer> activeAccounts = referenceDataRepository.getAllActiveAccountIds();
            log.info("Processing {} active accounts for EOD", activeAccounts.size());

            // Filter by pilot mode if enabled
            List<Integer> accountsToProcess = activeAccounts.stream()
                    .filter(id -> config.features().shouldProcessAccount(id))
                    .toList();

            if (accountsToProcess.size() < activeAccounts.size()) {
                log.info("Pilot mode: processing {} of {} accounts",
                        accountsToProcess.size(), activeAccounts.size());
            }

            // Process accounts in parallel batches
            int parallelism = config.batch().parallelism();
            int batchSize = config.batch().size();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < accountsToProcess.size(); i += batchSize) {
                int end = Math.min(i + batchSize, accountsToProcess.size());
                List<Integer> batch = accountsToProcess.subList(i, end);

                // Process batch in parallel
                List<CompletableFuture<AccountResult>> futures = new ArrayList<>();
                for (Integer accountId : batch) {
                    futures.add(processAccountAsync(accountId, businessDate));
                }

                // Wait for batch to complete
                for (CompletableFuture<AccountResult> future : futures) {
                    try {
                        AccountResult accountResult = future.join();
                        if (accountResult.success()) {
                            successCount.incrementAndGet();
                            result.addSuccess(accountResult.accountId());
                        } else {
                            failCount.incrementAndGet();
                            result.addFailure(accountResult.accountId(), accountResult.error());
                        }
                    } catch (Exception e) {
                        log.error("Async processing error: {}", e.getMessage());
                        failCount.incrementAndGet();
                    }
                }
            }

            // Set overall success
            result.setSuccess(failCount.get() == 0);
            result.setTotalProcessed(successCount.get() + failCount.get());

            // Log and alert
            log.info("EOD processing complete: success={}, failed={}", successCount.get(), failCount.get());

            if (failCount.get() > 0) {
                alertService.warning(AlertService.ALERT_EOD_FAILED,
                        String.format("EOD completed with %d failures out of %d accounts",
                                failCount.get(), accountsToProcess.size()),
                        "businessDate=" + businessDate);
            } else {
                alertService.info(AlertService.ALERT_EOD_DELAYED,
                        String.format("EOD completed successfully for %d accounts", successCount.get()),
                        "businessDate=" + businessDate);
            }

        } catch (Exception e) {
            log.error("Full EOD processing failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            // Use the correct overload - eodFailed(LocalDate, String)
            alertService.eodFailed(businessDate, e.getMessage());
        } finally {
            sample.stop(Timer.builder("eod.full")
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
    public void processEodForAccount(int accountId, LocalDate businessDate) {
        log.info("Processing EOD for account {} on {}", accountId, businessDate);

        try {
            eodProcessingService.processAccountEod(accountId, businessDate);
            meterRegistry.counter("eod.account.success").increment();
        } catch (Exception e) {
            log.error("EOD failed for account {}: {}", accountId, e.getMessage(), e);
            // Use the correct overload - eodFailed(int, String)
            alertService.eodFailed(accountId, e.getMessage());
            meterRegistry.counter("eod.account.failed").increment();
            throw e;
        }
    }

    /**
     * Process EOD batch from Kafka.
     * @param batch EOD batch message
     */
    public void processEodBatch(PositionDto.EodBatch batch) {
        log.info("Processing EOD batch: accountId={}, positions={}",
                batch.accountId(), batch.positions().size());

        try {
            eodProcessingService.processEodBatch(batch);
        } catch (Exception e) {
            log.error("EOD batch failed: accountId={}, error={}", batch.accountId(), e.getMessage(), e);
            // Use the correct overload - eodFailed(int, String)
            alertService.eodFailed(batch.accountId(), e.getMessage());
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASYNC PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process account EOD asynchronously.
     */
    @Async
    public CompletableFuture<AccountResult> processAccountAsync(int accountId, LocalDate businessDate) {
        try {
            eodProcessingService.processAccountEod(accountId, businessDate);
            return CompletableFuture.completedFuture(new AccountResult(accountId, true, null));
        } catch (Exception e) {
            log.error("Async EOD failed for account {}: {}", accountId, e.getMessage());
            return CompletableFuture.completedFuture(new AccountResult(accountId, false, e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of processing a single account.
     */
    public record AccountResult(int accountId, boolean success, String error) {}

    /**
     * Result of full EOD processing run.
     */
    public static class EodResult {
        private final LocalDate businessDate;
        private final List<Integer> successfulAccounts = new ArrayList<>();
        private final List<AccountFailure> failedAccounts = new ArrayList<>();
        private boolean success;
        private String error;
        private int totalProcessed;

        public EodResult(LocalDate businessDate) {
            this.businessDate = businessDate;
        }

        public void addSuccess(int accountId) {
            successfulAccounts.add(accountId);
        }

        public void addFailure(int accountId, String error) {
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

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public int getTotalProcessed() {
            return totalProcessed;
        }

        public void setTotalProcessed(int totalProcessed) {
            this.totalProcessed = totalProcessed;
        }

        public record AccountFailure(int accountId, String error) {}
    }
}