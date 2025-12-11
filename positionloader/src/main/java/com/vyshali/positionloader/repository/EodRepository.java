package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
            WHERE account_id = ? AND business_date = ? AND status = 'COMPLETED'
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, businessDate);
        return count != null && count > 0;
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
        String sql = "UPDATE eod_runs SET status = ? WHERE account_id = ? AND business_date = ?";
        jdbcTemplate.update(sql, status, accountId, businessDate);
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
    public java.util.List<EodRun> findPending() {
        String sql = """
            SELECT account_id, business_date, status, created_at 
            FROM eod_runs 
            WHERE status IN ('PENDING', 'IN_PROGRESS')
            ORDER BY created_at
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EodRun(
            rs.getInt("account_id"),
            rs.getDate("business_date").toLocalDate(),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ));
    }
    
    public record EodRun(int accountId, LocalDate businessDate, String status, LocalDateTime createdAt) {}
}
