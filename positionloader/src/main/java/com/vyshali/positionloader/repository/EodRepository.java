package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for EOD run tracking.
 */
@Repository
public class EodRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public EodRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Check if EOD is already completed.
     */
    public boolean isAlreadyCompleted(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT COUNT(*) FROM eod_runs 
            WHERE account_id = ? AND business_date = ? AND status IN ('COMPLETED', 'COMPLETE')
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, businessDate);
        return count != null && count > 0;
    }
    
    /**
     * Check if EOD is complete (alias for isAlreadyCompleted).
     */
    public boolean isComplete(int accountId, LocalDate businessDate) {
        return isAlreadyCompleted(accountId, businessDate);
    }
    
    /**
     * Create or update EOD run record.
     */
    @Transactional
    public void createOrUpdate(int accountId, LocalDate businessDate, String status) {
        String sql = """
            INSERT INTO eod_runs (account_id, business_date, status, started_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (account_id, business_date) 
            DO UPDATE SET status = ?, started_at = CURRENT_TIMESTAMP
            """;
        jdbcTemplate.update(sql, accountId, businessDate, status, status);
    }
    
    /**
     * Update status.
     */
    @Transactional
    public void updateStatus(int accountId, LocalDate businessDate, String status) {
        // First check if record exists
        String checkSql = "SELECT COUNT(*) FROM eod_runs WHERE account_id = ? AND business_date = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, accountId, businessDate);
        
        if (count != null && count > 0) {
            String sql = "UPDATE eod_runs SET status = ? WHERE account_id = ? AND business_date = ?";
            jdbcTemplate.update(sql, status, accountId, businessDate);
        } else {
            // Create new record if it doesn't exist
            createOrUpdate(accountId, businessDate, status);
        }
    }
    
    /**
     * Mark EOD as completed.
     */
    @Transactional
    public void markCompleted(int accountId, LocalDate businessDate, int positionCount) {
        String sql = """
            UPDATE eod_runs 
            SET status = 'COMPLETED', 
                position_count = ?,
                completed_at = CURRENT_TIMESTAMP
            WHERE account_id = ? AND business_date = ?
            """;
        jdbcTemplate.update(sql, positionCount, accountId, businessDate);
    }
    
    /**
     * Mark EOD as failed.
     */
    @Transactional
    public void markFailed(int accountId, LocalDate businessDate, String errorMessage) {
        String sql = """
            UPDATE eod_runs 
            SET status = 'FAILED', 
                error_message = ?,
                completed_at = CURRENT_TIMESTAMP
            WHERE account_id = ? AND business_date = ?
            """;
        jdbcTemplate.update(sql, errorMessage, accountId, businessDate);
    }
    
    /**
     * Get EOD status.
     */
    public String getStatus(int accountId, LocalDate businessDate) {
        String sql = "SELECT status FROM eod_runs WHERE account_id = ? AND business_date = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getString("status");
            }
            return null;
        }, accountId, businessDate);
    }
    
    /**
     * Find pending EOD runs.
     */
    public List<EodRun> findPending() {
        String sql = """
            SELECT account_id, business_date, status, started_at 
            FROM eod_runs 
            WHERE status IN ('PENDING', 'IN_PROGRESS', 'PROCESSING')
            ORDER BY started_at
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EodRun(
            rs.getInt("account_id"),
            rs.getDate("business_date").toLocalDate(),
            rs.getString("status"),
            rs.getTimestamp("started_at") != null ? 
                rs.getTimestamp("started_at").toLocalDateTime() : null
        ));
    }
    
    /**
     * Get EOD history for an account.
     */
    public List<EodRun> getHistory(int accountId, int days) {
        String sql = """
            SELECT account_id, business_date, status, started_at, completed_at, position_count, error_message
            FROM eod_runs 
            WHERE account_id = ? AND business_date >= CURRENT_DATE - ?
            ORDER BY business_date DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EodRun(
            rs.getInt("account_id"),
            rs.getDate("business_date").toLocalDate(),
            rs.getString("status"),
            rs.getTimestamp("started_at") != null ? 
                rs.getTimestamp("started_at").toLocalDateTime() : null,
            rs.getTimestamp("completed_at") != null ? 
                rs.getTimestamp("completed_at").toLocalDateTime() : null,
            rs.getInt("position_count"),
            rs.getString("error_message")
        ), accountId, days);
    }
    
    /**
     * Reset EOD status for reprocessing.
     */
    @Transactional
    public void resetStatus(int accountId, LocalDate businessDate) {
        String sql = """
            UPDATE eod_runs 
            SET status = 'PENDING', 
                started_at = NULL,
                completed_at = NULL,
                position_count = 0,
                error_message = NULL
            WHERE account_id = ? AND business_date = ?
            """;
        jdbcTemplate.update(sql, accountId, businessDate);
    }
    
    /**
     * Delete EOD run record.
     */
    @Transactional
    public void delete(int accountId, LocalDate businessDate) {
        String sql = "DELETE FROM eod_runs WHERE account_id = ? AND business_date = ?";
        jdbcTemplate.update(sql, accountId, businessDate);
    }
    
    /**
     * EOD run record.
     */
    public record EodRun(
        int accountId, 
        LocalDate businessDate, 
        String status, 
        LocalDateTime startedAt
    ) {
        public EodRun(int accountId, LocalDate businessDate, String status, 
                LocalDateTime startedAt, LocalDateTime completedAt, 
                int positionCount, String errorMessage) {
            this(accountId, businessDate, status, startedAt);
        }
    }
}
