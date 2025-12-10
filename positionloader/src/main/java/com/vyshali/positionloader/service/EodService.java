package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 12:58 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.config.FeatureFlags;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.EodProgress;
import com.vyshali.positionloader.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel EOD processing with progress tracking.
 * Combines: ParallelEodService + EodProgressTracker
 */
@Slf4j
@Service
public class EodService {

    private final MspmService mspm;
    private final SnapshotService snapshot;
    private final ValidationService validation;
    private final FeatureFlags flags;

    // In-memory progress tracking
    private final Map<LocalDate, RunState> runs = new ConcurrentHashMap<>();

    public EodService(MspmService mspm, SnapshotService snapshot, ValidationService validation, FeatureFlags flags) {
        this.mspm = mspm;
        this.snapshot = snapshot;
        this.validation = validation;
        this.flags = flags;
    }

    // ==================== MAIN ENTRY POINT ====================

    /**
     * Process EOD for all accounts in parallel.
     */
    public EodProgress.Result processAll(List<Integer> accountIds, LocalDate businessDate) {
        log.info("Starting EOD for {} accounts on {}", accountIds.size(), businessDate);

        Instant start = Instant.now();
        int maxConcurrency = flags.getEod().getMaxConcurrency();

        // Initialize tracking
        RunState state = new RunState(businessDate, accountIds.size());
        accountIds.forEach(id -> state.accounts.put(id, AccountState.PENDING));
        runs.put(businessDate, state);

        // Process in parallel with virtual threads
        Semaphore semaphore = new Semaphore(maxConcurrency);
        List<EodProgress.AccountResult> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<EodProgress.AccountResult>> futures = accountIds.stream().map(accountId -> CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    return processAccount(accountId, businessDate, state);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new EodProgress.AccountResult(accountId, false, "Interrupted", null);
                } finally {
                    semaphore.release();
                }
            }, executor)).toList();

            // Wait for completion with timeout
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(flags.getEod().getTimeoutMinutes(), TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("EOD timed out after {} minutes", flags.getEod().getTimeoutMinutes());
                state.timedOut = true;
            } catch (Exception e) {
                log.error("EOD error: {}", e.getMessage());
            }

            // Collect results
            for (var future : futures) {
                try {
                    results.add(future.getNow(null));
                } catch (Exception e) {
                    log.warn("Error collecting result: {}", e.getMessage());
                }
            }
        }

        // Calculate final stats
        Duration duration = Duration.between(start, Instant.now());
        int success = (int) results.stream().filter(r -> r != null && r.success()).count();
        int failed = results.size() - success;
        List<EodProgress.AccountResult> failures = results.stream().filter(r -> r != null && !r.success()).toList();

        log.info("EOD complete: {}/{} success in {}s", success, accountIds.size(), duration.toSeconds());

        // Auto-retry if enabled
        if (flags.getEod().isRetryFailed() && !failures.isEmpty()) {
            retry(failures.stream().map(EodProgress.AccountResult::accountId).toList(), businessDate);
        }

        return new EodProgress.Result(businessDate, accountIds.size(), success, failed, duration, failures, state.timedOut);
    }

    /**
     * Process single account.
     */
    private EodProgress.AccountResult processAccount(Integer accountId, LocalDate date, RunState state) {
        Instant start = Instant.now();
        state.accounts.put(accountId, AccountState.IN_PROGRESS);

        try {
            // Fetch
            AccountSnapshotDTO data = mspm.fetchSnapshot(accountId);
            if (data == null || !data.isAvailable()) {
                throw new RuntimeException("MSPM unavailable: " + (data != null ? data.status() : "null"));
            }

            // Validate
            ValidationResult valid = validation.validate(data);
            if (valid.hasErrors() && flags.getValidation().isStrictMode()) {
                throw new RuntimeException("Validation failed: " + valid.errorSummary());
            }

            // Save
            snapshot.saveSnapshot(data);

            state.accounts.put(accountId, AccountState.COMPLETED);
            state.successCount.incrementAndGet();

            return new EodProgress.AccountResult(accountId, true, null, data.positionCount(), Duration.between(start, Instant.now()));

        } catch (Exception e) {
            log.error("Failed account {}: {}", accountId, e.getMessage());
            state.accounts.put(accountId, AccountState.FAILED);
            state.failCount.incrementAndGet();
            state.errors.put(accountId, e.getMessage());

            return new EodProgress.AccountResult(accountId, false, e.getMessage(), null);
        }
    }

    // ==================== RETRY ====================

    public List<EodProgress.AccountResult> retry(List<Integer> accountIds, LocalDate date) {
        log.info("Retrying {} accounts", accountIds.size());

        RunState state = runs.getOrDefault(date, new RunState(date, accountIds.size()));

        return accountIds.stream().map(id -> processAccount(id, date, state)).toList();
    }

    // ==================== PROGRESS QUERIES ====================

    public EodProgress.Status getProgress(LocalDate date) {
        RunState state = runs.get(date);
        if (state == null) return null;

        int completed = 0, failed = 0, inProgress = 0, pending = 0;
        for (AccountState s : state.accounts.values()) {
            switch (s) {
                case COMPLETED -> completed++;
                case FAILED -> failed++;
                case IN_PROGRESS -> inProgress++;
                case PENDING -> pending++;
            }
        }

        return new EodProgress.Status(date, state.total, completed, failed, inProgress, pending, state.startTime, estimateCompletion(state, completed + failed, pending + inProgress));
    }

    public List<EodProgress.FailedAccount> getFailures(LocalDate date) {
        RunState state = runs.get(date);
        if (state == null) return List.of();

        return state.errors.entrySet().stream().map(e -> new EodProgress.FailedAccount(e.getKey(), e.getValue(), Instant.now(), 0)).toList();
    }

    public List<Integer> getPending(LocalDate date) {
        RunState state = runs.get(date);
        if (state == null) return List.of();

        return state.accounts.entrySet().stream().filter(e -> e.getValue() == AccountState.PENDING || e.getValue() == AccountState.IN_PROGRESS).map(Map.Entry::getKey).toList();
    }

    private String estimateCompletion(RunState state, int done, int remaining) {
        if (remaining == 0) return "Complete";
        if (done == 0) return "Calculating...";

        long elapsed = Duration.between(state.startTime, Instant.now()).toSeconds();
        if (elapsed > 0) {
            double rate = (double) done / elapsed;
            long secondsLeft = (long) (remaining / rate);
            return Instant.now().plusSeconds(secondsLeft).toString();
        }
        return "Unknown";
    }

    // ==================== INTERNAL STATE ====================

    private enum AccountState {PENDING, IN_PROGRESS, COMPLETED, FAILED}

    private static class RunState {
        final LocalDate date;
        final int total;
        final Instant startTime = Instant.now();
        final Map<Integer, AccountState> accounts = new ConcurrentHashMap<>();
        final Map<Integer, String> errors = new ConcurrentHashMap<>();
        final AtomicInteger successCount = new AtomicInteger();
        final AtomicInteger failCount = new AtomicInteger();
        volatile boolean timedOut = false;

        RunState(LocalDate date, int total) {
            this.date = date;
            this.total = total;
        }
    }
}