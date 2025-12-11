package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Repository for batch operations.
 * Manages blue/green deployment batches for positions.
 */
@Repository
public class BatchRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public BatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Get the next batch ID for an account.
     */
    public int getNextBatchId(int accountId) {
        String sql = "SELECT COALESCE(MAX(batch_id), 0) + 1 FROM account_batches WHERE account_id = ?";
        Integer nextId = jdbcTemplate.queryForObject(sql, Integer.class, accountId);
        return nextId != null ? nextId : 1;
    }
    
    /**
     * Create a new batch and return the batch ID.
     */
    @Transactional
    public int createBatch(int accountId, LocalDate businessDate, String source) {
        int batchId = getNextBatchId(accountId);
        String sql = """
            INSERT INTO account_batches (account_id, batch_id, business_date, status, source, created_at)
            VALUES (?, ?, ?, 'STAGING', ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql, accountId, batchId, businessDate, source);
        return batchId;
    }
    
    /**
     * Create a new batch with status (legacy signature).
     */
    @Transactional
    public void createBatch(int accountId, int batchId, LocalDate businessDate, String status) {
        String sql = """
            INSERT INTO account_batches (account_id, batch_id, business_date, status, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql, accountId, batchId, businessDate, status);
    }
    
    /**
     * Update batch status.
     */
    @Transactional
    public void updateStatus(int batchId, String status) {
        String sql = "UPDATE account_batches SET status = ? WHERE batch_id = ?";
        jdbcTemplate.update(sql, status, batchId);
    }
    
    /**
     * Update batch status by account and batch ID.
     */
    @Transactional
    public void updateStatus(int accountId, int batchId, String status) {
        String sql = "UPDATE account_batches SET status = ? WHERE account_id = ? AND batch_id = ?";
        jdbcTemplate.update(sql, status, accountId, batchId);
    }
    
    /**
     * Complete a batch - mark as ACTIVE and set position count.
     */
    @Transactional
    public void completeBatch(int batchId, int positionCount) {
        // First, archive any existing active batch for this account
        String archiveSql = """
            UPDATE account_batches 
            SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP 
            WHERE account_id = (SELECT account_id FROM account_batches WHERE batch_id = ?)
            AND status = 'ACTIVE'
            """;
        jdbcTemplate.update(archiveSql, batchId);
        
        // Then activate this batch
        String sql = """
            UPDATE account_batches 
            SET status = 'ACTIVE', 
                position_count = ?, 
                activated_at = CURRENT_TIMESTAMP 
            WHERE batch_id = ?
            """;
        jdbcTemplate.update(sql, positionCount, batchId);
    }
    
    /**
     * Fail a batch - mark as FAILED with error message.
     */
    @Transactional
    public void failBatch(int batchId, String errorMessage) {
        String sql = """
            UPDATE account_batches 
            SET status = 'FAILED', 
                error_message = ? 
            WHERE batch_id = ?
            """;
        jdbcTemplate.update(sql, errorMessage, batchId);
    }
    
    /**
     * Update position count.
     */
    @Transactional
    public void updatePositionCount(int accountId, int batchId, int count) {
        String sql = "UPDATE account_batches SET position_count = ? WHERE account_id = ? AND batch_id = ?";
        jdbcTemplate.update(sql, count, accountId, batchId);
    }
    
    /**
     * Set activated timestamp.
     */
    @Transactional
    public void setActivatedAt(int accountId, int batchId, LocalDateTime activatedAt) {
        String sql = "UPDATE account_batches SET activated_at = ? WHERE account_id = ? AND batch_id = ?";
        jdbcTemplate.update(sql, activatedAt, accountId, batchId);
    }
    
    /**
     * Set archived timestamp.
     */
    @Transactional
    public void setArchivedAt(int accountId, int batchId, LocalDateTime archivedAt) {
        String sql = "UPDATE account_batches SET archived_at = ? WHERE account_id = ? AND batch_id = ?";
        jdbcTemplate.update(sql, archivedAt, accountId, batchId);
    }
    
    /**
     * Set error message.
     */
    @Transactional
    public void setErrorMessage(int accountId, int batchId, String errorMessage) {
        String sql = "UPDATE account_batches SET error_message = ?, status = 'FAILED' " +
            "WHERE account_id = ? AND batch_id = ?";
        jdbcTemplate.update(sql, errorMessage, accountId, batchId);
    }
    
    /**
     * Find the current active batch for an account.
     */
    public Integer findActiveBatch(int accountId) {
        String sql = """
            SELECT batch_id FROM account_batches 
            WHERE account_id = ? AND status = 'ACTIVE'
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("batch_id");
            }
            return null;
        }, accountId);
    }
    
    /**
     * Find the previous active batch (for rollback).
     */
    public Integer findPreviousActiveBatch(int accountId, int currentBatchId) {
        String sql = """
            SELECT batch_id FROM account_batches 
            WHERE account_id = ? AND batch_id < ? AND status = 'ARCHIVED'
            ORDER BY batch_id DESC LIMIT 1
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("batch_id");
            }
            return null;
        }, accountId, currentBatchId);
    }
    
    /**
     * Check if a batch exists.
     */
    public boolean exists(int accountId, int batchId) {
        String sql = "SELECT COUNT(*) FROM account_batches WHERE account_id = ? AND batch_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, batchId);
        return count != null && count > 0;
    }
    
    /**
     * Get batch status.
     */
    public String getStatus(int accountId, int batchId) {
        String sql = "SELECT status FROM account_batches WHERE account_id = ? AND batch_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, accountId, batchId);
    }
    
    /**
     * Rollback to previous batch.
     */
    @Transactional
    public boolean rollbackToPrevious(int accountId) {
        Integer activeBatch = findActiveBatch(accountId);
        if (activeBatch == null) {
            return false;
        }
        
        Integer previousBatch = findPreviousActiveBatch(accountId, activeBatch);
        if (previousBatch == null) {
            return false;
        }
        
        // Mark current as rolled back
        updateStatus(accountId, activeBatch, "ROLLED_BACK");
        
        // Reactivate previous batch
        updateStatus(accountId, previousBatch, "ACTIVE");
        setActivatedAt(accountId, previousBatch, LocalDateTime.now());
        
        return true;
    }
}
