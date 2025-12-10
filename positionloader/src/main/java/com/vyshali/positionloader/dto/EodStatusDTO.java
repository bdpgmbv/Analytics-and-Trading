package com.vyshali.positionloader.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for EOD run status.
 */
public record EodStatusDTO(Integer accountId, LocalDate businessDate, String status,
                           // RUNNING, COMPLETED, FAILED
                           Integer positionCount, LocalDateTime startedAt, LocalDateTime completedAt,
                           String errorMessage) {
    /**
     * Check if EOD completed successfully.
     */
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    /**
     * Check if EOD is currently running.
     */
    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    /**
     * Check if EOD failed.
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * Get duration in seconds (if completed).
     */
    public Long durationSeconds() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, completedAt).getSeconds();
    }
}