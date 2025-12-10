package com.vyshali.positionloader.dto;

/*
 * 12/10/2025 - 1:59 PM
 * @author Vyshali Prabananth Lal
 */

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EOD processing progress and status tracking.
 */
public final class EodProgress {

    private EodProgress() {
    }

    /**
     * Overall EOD status for a business date.
     */
    public record Status(LocalDate businessDate, int total, int completed, int failed, int pending, int inProgress,
                         LocalDateTime lastUpdate, String overallStatus) {
        public double percentComplete() {
            return total > 0 ? (completed * 100.0 / total) : 0;
        }
    }

    /**
     * Failed account details.
     */
    public record FailedAccount(Integer accountId, String accountName, String errorMessage, LocalDateTime failedAt,
                                int retryCount) {
    }

    /**
     * Result of processing a single account.
     */
    public record AccountResult(Integer accountId, boolean success, String message) {
    }
}
