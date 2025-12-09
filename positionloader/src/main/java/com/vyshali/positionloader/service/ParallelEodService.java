package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:31 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #1: Parallel EOD Processing
 *
 * Problem: Sequential processing of 4,000 accounts takes 2+ hours
 * Solution: Virtual thread-based parallelism with configurable concurrency
 *
 * Performance:
 * - Sequential: 4,000 accounts × 2s = 8,000s (2.2 hours)
 * - Parallel (50): 4,000 accounts / 50 × 2s = 160s (2.7 minutes)
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDetailDTO;
import com.vyshali.positionloader.metrics.LoaderMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ParallelEodService {

    private static final Logger log = LoggerFactory.getLogger(ParallelEodService.class);

    private final MspmIntegrationService mspmService;
    private final SnapshotService snapshotService;
    private final PositionValidationService validationService;
    private final EodProgressTracker progressTracker;
    private final LoaderMetrics metrics;
    private final MeterRegistry meterRegistry;

    @Value("${eod.parallel.max-concurrency:50}")
    private int maxConcurrency;

    @Value("${eod.parallel.timeout-minutes:30}")
    private int timeoutMinutes;

    @Value("${eod.parallel.retry-failed:true}")
    private boolean retryFailed;

    @Value("${eod.validation.strict-mode:false}")
    private boolean strictValidation;

    private final ConcurrentHashMap<LocalDate, EodRunState> eodRuns = new ConcurrentHashMap<>();

    public ParallelEodService(MspmIntegrationService mspmService, SnapshotService snapshotService, PositionValidationService validationService, EodProgressTracker progressTracker, LoaderMetrics metrics, MeterRegistry meterRegistry) {
        this.mspmService = mspmService;
        this.snapshotService = snapshotService;
        this.validationService = validationService;
        this.progressTracker = progressTracker;
        this.metrics = metrics;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Main entry point for EOD processing
     */
    public EodResult processEodForAllAccounts(List<Integer> accountIds, LocalDate businessDate) {
        log.info("Starting parallel EOD for {} accounts on {}", accountIds.size(), businessDate);

        Instant startTime = Instant.now();
        Timer.Sample timerSample = Timer.start(meterRegistry);

        EodRunState runState = new EodRunState(businessDate, accountIds.size());
        eodRuns.put(businessDate, runState);
        progressTracker.initializeRun(businessDate, accountIds);

        Semaphore semaphore = new Semaphore(maxConcurrency);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<CompletableFuture<AccountResult>> futures = new ArrayList<>();

            for (Integer accountId : accountIds) {
                CompletableFuture<AccountResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        return processAccount(accountId, businessDate);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new AccountResult(accountId, false, "Interrupted", null);
                    } finally {
                        semaphore.release();
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            try {
                allFutures.get(timeoutMinutes, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("EOD timed out after {} minutes", timeoutMinutes);
                runState.setTimedOut(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("EOD interrupted");
            } catch (ExecutionException e) {
                log.error("EOD execution error", e);
            }

            List<AccountResult> results = new ArrayList<>();
            List<AccountResult> failures = new ArrayList<>();

            for (CompletableFuture<AccountResult> future : futures) {
                try {
                    AccountResult result = future.getNow(null);
                    if (result != null) {
                        results.add(result);
                        if (!result.success()) {
                            failures.add(result);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error getting result: {}", e.getMessage());
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());
            timerSample.stop(meterRegistry.timer("posloader.eod.total_duration"));

            int successCount = (int) results.stream().filter(AccountResult::success).count();
            int failCount = failures.size();

            metrics.recordEodCompletion(successCount, failCount, duration);
            progressTracker.completeRun(businessDate, successCount, failCount);

            log.info("EOD completed: {}/{} successful in {} seconds", successCount, accountIds.size(), duration.toSeconds());

            if (retryFailed && !failures.isEmpty()) {
                log.info("Retrying {} failed accounts", failures.size());
                retryFailedAccounts(failures, businessDate);
            }

            return new EodResult(businessDate, accountIds.size(), successCount, failCount, duration, failures, runState.isTimedOut());
        }
    }

    /**
     * Process a single account with full validation
     */
    private AccountResult processAccount(Integer accountId, LocalDate businessDate) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Instant start = Instant.now();

        try {
            progressTracker.markInProgress(businessDate, accountId);

            AccountSnapshotDTO snapshot = mspmService.fetchEodSnapshot(accountId);

            if (snapshot == null || "Unavailable".equals(snapshot.status())) {
                throw new RuntimeException("MSPM returned unavailable for account " + accountId);
            }

            PositionValidationService.ValidationResult validation = validationService.validateSnapshot(snapshot);

            if (validation.hasErrors()) {
                if (strictValidation) {
                    metrics.recordValidationFailure(accountId, validation.errorCount());
                    throw new RuntimeException("Validation failed: " + validation.errorSummary());
                } else {
                    log.warn("Account {} has validation warnings: {}", accountId, validation.errorSummary());
                }
            }

            long zeroPriceCount = countZeroPrices(snapshot.positions());
            if (zeroPriceCount > 0) {
                double zeroPct = (double) zeroPriceCount / snapshot.positions().size() * 100;
                metrics.recordZeroPriceDetection(accountId, zeroPriceCount);

                if (zeroPct > 10) {
                    log.error("ALERT: Account {} has {}% zero prices - Price Service may be down!", accountId, String.format("%.1f", zeroPct));
                    if (strictValidation) {
                        throw new RuntimeException("Too many zero-price positions: " + zeroPriceCount);
                    }
                }
            }

            snapshotService.saveSnapshotToDb(snapshot);

            Duration duration = Duration.between(start, Instant.now());
            sample.stop(meterRegistry.timer("posloader.account.processing_time", "status", "success"));

            metrics.recordAccountSuccess(accountId, snapshot.positions().size(), duration);
            progressTracker.markComplete(businessDate, accountId);

            return new AccountResult(accountId, true, null, snapshot.positions().size());

        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            sample.stop(meterRegistry.timer("posloader.account.processing_time", "status", "failure"));

            log.error("Failed to process account {}: {}", accountId, e.getMessage());
            metrics.recordAccountFailure(accountId, e.getMessage());
            progressTracker.markFailed(businessDate, accountId, e.getMessage());

            return new AccountResult(accountId, false, e.getMessage(), null);
        }
    }

    private long countZeroPrices(List<PositionDetailDTO> positions) {
        if (positions == null) return 0;
        return positions.stream().filter(p -> p.price() != null && p.price().signum() == 0).count();
    }

    private void retryFailedAccounts(List<AccountResult> failures, LocalDate businessDate) {
        List<Integer> failedIds = failures.stream().map(AccountResult::accountId).toList();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        int retrySuccess = 0;
        for (Integer accountId : failedIds) {
            try {
                AccountResult result = processAccount(accountId, businessDate);
                if (result.success()) {
                    retrySuccess++;
                    log.info("Retry successful for account {}", accountId);
                }
            } catch (Exception e) {
                log.warn("Retry failed for account {}: {}", accountId, e.getMessage());
            }
        }

        log.info("Retry complete: {}/{} recovered", retrySuccess, failedIds.size());
    }

    public List<AccountResult> retryAccounts(List<Integer> accountIds, LocalDate businessDate) {
        log.info("Manual retry for {} accounts", accountIds.size());

        List<AccountResult> results = new ArrayList<>();
        for (Integer accountId : accountIds) {
            results.add(processAccount(accountId, businessDate));
        }
        return results;
    }

    public EodProgress getProgress(LocalDate businessDate) {
        return progressTracker.getProgress(businessDate);
    }

    public List<FailedAccount> getFailedAccounts(LocalDate businessDate) {
        return progressTracker.getFailedAccounts(businessDate);
    }

    // Inner Classes
    public record AccountResult(Integer accountId, boolean success, String errorMessage, Integer positionCount) {
    }

    public record EodResult(LocalDate businessDate, int totalAccounts, int successCount, int failCount,
                            Duration duration, List<AccountResult> failures, boolean timedOut) {
        public double successRate() {
            return totalAccounts > 0 ? (double) successCount / totalAccounts * 100 : 0;
        }
    }

    public record EodProgress(LocalDate businessDate, int total, int completed, int failed, int inProgress, int pending,
                              double percentComplete, Instant startTime, String estimatedCompletion) {
    }

    public record FailedAccount(Integer accountId, String errorMessage, Instant failedAt, int retryCount) {
    }

    private static class EodRunState {
        private final LocalDate businessDate;
        private final int totalAccounts;
        private volatile boolean timedOut = false;

        EodRunState(LocalDate businessDate, int totalAccounts) {
            this.businessDate = businessDate;
            this.totalAccounts = totalAccounts;
        }

        boolean isTimedOut() {
            return timedOut;
        }

        void setTimedOut(boolean timedOut) {
            this.timedOut = timedOut;
        }
    }
}