package com.vyshali.positionloader.repository;

import com.fxanalyzer.positionloader.service.ArchivalService.ArchivalStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Repository for position archival operations.
 */
@Repository
public class ArchivalRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ArchivalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Archive a batch of positions older than cutoff date.
     */
    @Transactional
    public int archiveBatch(LocalDate cutoffDate, int batchSize) {
        String sql = """
            WITH archived AS (
                DELETE FROM positions 
                WHERE position_id IN (
                    SELECT position_id FROM positions 
                    WHERE business_date < ? 
                    AND system_to = '9999-12-31 23:59:59'
                    LIMIT ?
                )
                RETURNING position_id, account_id, product_id, business_date, 
                    quantity, price, currency, market_value_local, market_value_base,
                    batch_id, source
            )
            INSERT INTO positions_archive (
                original_position_id, account_id, product_id, business_date,
                quantity, price, currency, market_value_local, market_value_base,
                batch_id, source, archive_reason
            )
            SELECT position_id, account_id, product_id, business_date,
                quantity, price, currency, market_value_local, market_value_base,
                batch_id, source, 'RETENTION_POLICY'
            FROM archived
            """;
        return jdbcTemplate.update(sql, cutoffDate, batchSize);
    }
    
    /**
     * Archive positions for a specific account.
     */
    @Transactional
    public int archiveByAccount(int accountId, LocalDate olderThan) {
        String sql = """
            WITH archived AS (
                DELETE FROM positions 
                WHERE account_id = ? AND business_date < ?
                RETURNING position_id, account_id, product_id, business_date,
                    quantity, price, currency, market_value_local, market_value_base,
                    batch_id, source
            )
            INSERT INTO positions_archive (
                original_position_id, account_id, product_id, business_date,
                quantity, price, currency, market_value_local, market_value_base,
                batch_id, source, archive_reason
            )
            SELECT position_id, account_id, product_id, business_date,
                quantity, price, currency, market_value_local, market_value_base,
                batch_id, source, 'MANUAL_ARCHIVAL'
            FROM archived
            """;
        return jdbcTemplate.update(sql, accountId, olderThan);
    }
    
    /**
     * Purge old archived positions.
     */
    @Transactional
    public int purgeArchived(LocalDate olderThan) {
        String sql = "DELETE FROM positions_archive WHERE archived_at < ?";
        return jdbcTemplate.update(sql, olderThan);
    }
    
    /**
     * Get archival statistics.
     */
    public ArchivalStats getStats() {
        String sql = """
            SELECT 
                COUNT(*) as total,
                MIN(business_date) as oldest,
                MAX(business_date) as newest,
                pg_total_relation_size('positions_archive') as size_bytes
            FROM positions_archive
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ArchivalStats(
            rs.getLong("total"),
            rs.getDate("oldest") != null ? rs.getDate("oldest").toLocalDate() : null,
            rs.getDate("newest") != null ? rs.getDate("newest").toLocalDate() : null,
            rs.getLong("size_bytes")
        ));
    }
    
    /**
     * Count archived positions for an account.
     */
    public long countByAccount(int accountId) {
        String sql = "SELECT COUNT(*) FROM positions_archive WHERE account_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, accountId);
        return count != null ? count : 0;
    }
}
