package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 12:51 PM
 * @author Vyshali Prabananth Lal
 */

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * EOD processing progress and result DTOs.
 */
public final class EodProgress {

    private EodProgress() {
    } // Utility class

    /**
     * Current progress of an EOD run.
     */
    public record Status(LocalDate businessDate, int total, int completed, int failed, int inProgress, int pending,
                         Instant startTime, String estimatedCompletion) {
        public double percentComplete() {
            return total > 0 ? (double) (completed + failed) / total * 100 : 0;
        }

        public String state() {
            if (percentComplete() >= 100) {
                return failed > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED";
            }
            if (inProgress > 0) return "IN_PROGRESS";
            if (pending > 0) return "PENDING";
            return "NOT_STARTED";
        }
    }

    /**
     * Result of processing a single account.
     */
    public record AccountResult(Integer accountId, boolean success, String errorMessage, Integer positionCount,
                                Duration duration) {
        public AccountResult(Integer accountId, boolean success, String errorMessage, Integer positionCount) {
            this(accountId, success, errorMessage, positionCount, null);
        }
    }

    /**
     * Failed account details for retry.
     */
    public record FailedAccount(Integer accountId, String errorMessage, Instant failedAt, int retryCount) {
    }

    /**
     * Final EOD run result.
     */
    public record Result(LocalDate businessDate, int total, int success, int failed, Duration duration,
                         List<AccountResult> failures, boolean timedOut) {
        public double successRate() {
            return total > 0 ? (double) success / total * 100 : 0;
        }
    }

    /**
     * Progress for a single client's accounts.
     */
    public record ClientProgress(String clientId, int total, int completed, int failed, int pending) {
        public double percentComplete() {
            return total > 0 ? (double) completed / total * 100 : 0;
        }
    }
}