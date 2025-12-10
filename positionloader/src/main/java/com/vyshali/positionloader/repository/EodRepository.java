package com.vyshali.positionloader.repository;

/*
 * Repository for EOD status queries.
 * Used directly by EodController (no service layer needed for simple queries).
 */

import com.vyshali.positionloader.dto.EodStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class EodRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get EOD status for an account on a specific date.
     */
    public EodStatusDTO getEodStatus(Integer accountId, LocalDate businessDate) {
        String sql = """
                SELECT account_id, business_date, status, 
                       position_count, started_at, completed_at, error_message
                FROM eod_runs
                WHERE account_id = ?
                  AND business_date = ?
                ORDER BY started_at DESC
                LIMIT 1
                """;

        List<EodStatusDTO> results = jdbcTemplate.query(sql, new EodStatusRowMapper(), accountId, businessDate);

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get EOD history for an account.
     */
    public List<EodStatusDTO> getEodHistory(Integer accountId, int days) {
        String sql = """
                SELECT account_id, business_date, status,
                       position_count, started_at, completed_at, error_message
                FROM eod_runs
                WHERE account_id = ?
                  AND business_date >= CURRENT_DATE - INTERVAL '%d days'
                ORDER BY business_date DESC, started_at DESC
                """.formatted(days);

        return jdbcTemplate.query(sql, new EodStatusRowMapper(), accountId);
    }

    /**
     * Record EOD start.
     */
    public void recordEodStart(Integer accountId, LocalDate businessDate) {
        String sql = """
                INSERT INTO eod_runs (account_id, business_date, status, started_at)
                VALUES (?, ?, 'RUNNING', NOW())
                ON CONFLICT (account_id, business_date) 
                DO UPDATE SET status = 'RUNNING', started_at = NOW(), error_message = NULL
                """;

        jdbcTemplate.update(sql, accountId, businessDate);
    }

    /**
     * Record EOD completion.
     */
    public void recordEodComplete(Integer accountId, LocalDate businessDate, int positionCount) {
        String sql = """
                UPDATE eod_runs 
                SET status = 'COMPLETED', 
                    completed_at = NOW(), 
                    position_count = ?
                WHERE account_id = ? 
                  AND business_date = ?
                """;

        jdbcTemplate.update(sql, positionCount, accountId, businessDate);
    }

    /**
     * Record EOD failure.
     */
    public void recordEodFailed(Integer accountId, LocalDate businessDate, String errorMessage) {
        String sql = """
                UPDATE eod_runs 
                SET status = 'FAILED', 
                    completed_at = NOW(), 
                    error_message = ?
                WHERE account_id = ? 
                  AND business_date = ?
                """;

        jdbcTemplate.update(sql, errorMessage, accountId, businessDate);
    }

    /**
     * Get accounts that haven't completed EOD today.
     */
    public List<Integer> getPendingAccounts(LocalDate businessDate) {
        String sql = """
                SELECT DISTINCT a.account_id
                FROM accounts a
                LEFT JOIN eod_runs e ON a.account_id = e.account_id 
                                    AND e.business_date = ?
                WHERE a.active = true
                  AND (e.status IS NULL OR e.status != 'COMPLETED')
                """;

        return jdbcTemplate.queryForList(sql, Integer.class, businessDate);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROW MAPPER
    // ═══════════════════════════════════════════════════════════════════════════

    private static class EodStatusRowMapper implements RowMapper<EodStatusDTO> {
        @Override
        public EodStatusDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EodStatusDTO(rs.getInt("account_id"), rs.getDate("business_date").toLocalDate(), rs.getString("status"), rs.getInt("position_count"), rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toLocalDateTime() : null, rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toLocalDateTime() : null, rs.getString("error_message"));
        }
    }
}