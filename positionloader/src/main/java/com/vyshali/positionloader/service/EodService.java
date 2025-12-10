package com.vyshali.positionloader.service;

/*
 * 12/10/2025 - 2:09 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.EodProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EOD progress tracking and retry service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodService {

    private final JdbcTemplate jdbc;
    private final SnapshotService snapshotService;

    /**
     * Get EOD progress for a business date.
     */
    public EodProgress.Status getProgress(LocalDate date) {
        try {
            Integer total = jdbc.queryForObject("SELECT COUNT(DISTINCT account_id) FROM Accounts", Integer.class);

            Integer completed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM Eod_Daily_Status
                    WHERE business_date = ? AND status = 'COMPLETED'
                    """, Integer.class, date);

            Integer failed = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM Eod_Daily_Status
                    WHERE business_date = ? AND status = 'FAILED'
                    """, Integer.class, date);

            total = total != null ? total : 0;
            completed = completed != null ? completed : 0;
            failed = failed != null ? failed : 0;
            int pending = total - completed - failed;

            String status = "IN_PROGRESS";
            if (completed >= total) status = "COMPLETED";
            else if (failed > 0) status = "PARTIAL";

            return new EodProgress.Status(date, total, completed, failed, pending, 0, LocalDateTime.now(), status);

        } catch (Exception e) {
            log.error("Failed to get EOD progress: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get failed accounts for a business date.
     */
    public List<EodProgress.FailedAccount> getFailures(LocalDate date) {
        try {
            return jdbc.query("""
                    SELECT e.account_id, a.account_number, e.created_at
                    FROM Eod_Daily_Status e
                    LEFT JOIN Accounts a ON e.account_id = a.account_id
                    WHERE e.business_date = ? AND e.status = 'FAILED'
                    """, (rs, rowNum) -> new EodProgress.FailedAccount(rs.getInt("account_id"), rs.getString("account_number"), "EOD processing failed", rs.getTimestamp("created_at").toLocalDateTime(), 0), date);
        } catch (Exception e) {
            log.error("Failed to get failures: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get pending accounts for a business date.
     */
    public List<Integer> getPending(LocalDate date) {
        try {
            return jdbc.query("""
                    SELECT a.account_id FROM Accounts a
                    WHERE a.account_id NOT IN (
                        SELECT account_id FROM Eod_Daily_Status WHERE business_date = ?
                    )
                    """, (rs, rowNum) -> rs.getInt("account_id"), date);
        } catch (Exception e) {
            log.error("Failed to get pending: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Retry failed accounts.
     */
    public List<EodProgress.AccountResult> retry(List<Integer> accountIds, LocalDate date) {
        List<EodProgress.AccountResult> results = new ArrayList<>();

        for (Integer accountId : accountIds) {
            try {
                snapshotService.processEod(accountId);
                results.add(new EodProgress.AccountResult(accountId, true, "Success"));
            } catch (Exception e) {
                log.error("Retry failed for account {}: {}", accountId, e.getMessage());
                results.add(new EodProgress.AccountResult(accountId, false, e.getMessage()));
            }
        }

        return results;
    }
}
