package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:45 PM
 * @author Vyshali Prabananth Lal
 */


/*
 * CRITICAL FIX #6: EOD Progress Tracking
 *
 * Problem: Ops team has NO visibility into EOD progress
 *
 * @author Vyshali Prabananth Lal
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EodProgressTracker {

    private static final Logger log = LoggerFactory.getLogger(EodProgressTracker.class);

    private final Map<LocalDate, EodRunProgress> runs = new ConcurrentHashMap<>();

    public void initializeRun(LocalDate businessDate, List<Integer> accountIds) {
        EodRunProgress progress = new EodRunProgress(businessDate, accountIds.size());

        for (Integer accountId : accountIds) {
            progress.accounts.put(accountId, new AccountProgress(accountId, AccountStatus.PENDING));
        }

        runs.put(businessDate, progress);
        log.info("Initialized EOD run for {} with {} accounts", businessDate, accountIds.size());
    }

    public void markInProgress(LocalDate businessDate, Integer accountId) {
        EodRunProgress run = runs.get(businessDate);
        if (run != null) {
            AccountProgress account = run.accounts.get(accountId);
            if (account != null) {
                account.status = AccountStatus.IN_PROGRESS;
                account.startedAt = Instant.now();
            }
        }
    }

    public void markComplete(LocalDate businessDate, Integer accountId) {
        EodRunProgress run = runs.get(businessDate);
        if (run != null) {
            AccountProgress account = run.accounts.get(accountId);
            if (account != null) {
                account.status = AccountStatus.COMPLETED;
                account.completedAt = Instant.now();
                if (account.startedAt != null) {
                    account.duration = Duration.between(account.startedAt, account.completedAt);
                }
                run.successCount.incrementAndGet();
            }
        }
    }

    public void markFailed(LocalDate businessDate, Integer accountId, String error) {
        EodRunProgress run = runs.get(businessDate);
        if (run != null) {
            AccountProgress account = run.accounts.get(accountId);
            if (account != null) {
                account.status = AccountStatus.FAILED;
                account.completedAt = Instant.now();
                account.errorMessage = error;
                run.failCount.incrementAndGet();
            }
        }
    }

    public void completeRun(LocalDate businessDate, int success, int failed) {
        EodRunProgress run = runs.get(businessDate);
        if (run != null) {
            run.completedAt = Instant.now();
            run.isComplete = true;
            log.info("EOD run completed for {}: {} success, {} failed", businessDate, success, failed);
        }
    }

    public ParallelEodService.EodProgress getProgress(LocalDate businessDate) {
        EodRunProgress run = runs.get(businessDate);
        if (run == null) {
            return null;
        }

        int completed = 0, failed = 0, inProgress = 0, pending = 0;

        for (AccountProgress account : run.accounts.values()) {
            switch (account.status) {
                case COMPLETED -> completed++;
                case FAILED -> failed++;
                case IN_PROGRESS -> inProgress++;
                case PENDING -> pending++;
            }
        }

        double percentComplete = run.totalAccounts > 0 ? (double) (completed + failed) / run.totalAccounts * 100 : 0;

        String estimatedCompletion = estimateCompletion(run, completed + failed, inProgress + pending);

        return new ParallelEodService.EodProgress(businessDate, run.totalAccounts, completed, failed, inProgress, pending, percentComplete, run.startedAt, estimatedCompletion);
    }

    private String estimateCompletion(EodRunProgress run, int processed, int remaining) {
        if (remaining == 0) return "Complete";
        if (processed == 0) return "Calculating...";

        Duration elapsed = Duration.between(run.startedAt, Instant.now());
        double rate = (double) processed / elapsed.toSeconds();

        if (rate > 0) {
            long secondsRemaining = (long) (remaining / rate);
            Instant estimatedEnd = Instant.now().plusSeconds(secondsRemaining);
            return estimatedEnd.toString();
        }

        return "Unknown";
    }

    public List<ParallelEodService.FailedAccount> getFailedAccounts(LocalDate businessDate) {
        EodRunProgress run = runs.get(businessDate);
        if (run == null) return Collections.emptyList();

        return run.accounts.values().stream().filter(a -> a.status == AccountStatus.FAILED).map(a -> new ParallelEodService.FailedAccount(a.accountId, a.errorMessage, a.completedAt, a.retryCount)).collect(Collectors.toList());
    }

    public List<Integer> getPendingAccounts(LocalDate businessDate) {
        EodRunProgress run = runs.get(businessDate);
        if (run == null) return Collections.emptyList();

        return run.accounts.values().stream().filter(a -> a.status == AccountStatus.PENDING || a.status == AccountStatus.IN_PROGRESS).map(a -> a.accountId).collect(Collectors.toList());
    }

    public Map<String, ClientProgress> getProgressByClient(LocalDate businessDate) {
        EodRunProgress run = runs.get(businessDate);
        if (run == null) return Collections.emptyMap();

        Map<String, List<AccountProgress>> byClient = run.accounts.values().stream().collect(Collectors.groupingBy(a -> "Client-" + (a.accountId / 100)));

        return byClient.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            List<AccountProgress> accounts = e.getValue();
            int total = accounts.size();
            int complete = (int) accounts.stream().filter(a -> a.status == AccountStatus.COMPLETED).count();
            int failed = (int) accounts.stream().filter(a -> a.status == AccountStatus.FAILED).count();
            return new ClientProgress(e.getKey(), total, complete, failed, total - complete - failed, (double) complete / total * 100);
        }));
    }

    // Inner Classes
    private static class EodRunProgress {
        final LocalDate businessDate;
        final int totalAccounts;
        final Instant startedAt;
        Instant completedAt;
        boolean isComplete;
        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger();
        final Map<Integer, AccountProgress> accounts = new ConcurrentHashMap<>();

        EodRunProgress(LocalDate businessDate, int totalAccounts) {
            this.businessDate = businessDate;
            this.totalAccounts = totalAccounts;
            this.startedAt = Instant.now();
        }
    }

    private static class AccountProgress {
        final Integer accountId;
        AccountStatus status;
        Instant startedAt;
        Instant completedAt;
        Duration duration;
        String errorMessage;
        int retryCount = 0;

        AccountProgress(Integer accountId, AccountStatus status) {
            this.accountId = accountId;
            this.status = status;
        }
    }

    public enum AccountStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    public record ClientProgress(String clientId, int totalAccounts, int completed, int failed, int pending,
                                 double percentComplete) {
    }
}
