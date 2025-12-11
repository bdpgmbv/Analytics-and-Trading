package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Repository for snapshot hash operations (Phase 4 #19 - Duplicate Detection).
 */
@Repository
public class SnapshotHashRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public SnapshotHashRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Find hash for account and date.
     */
    public String findHash(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT content_hash FROM snapshot_hashes 
            WHERE account_id = ? AND business_date = ?
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getString("content_hash");
            }
            return null;
        }, accountId, businessDate);
    }
    
    /**
     * Insert or update hash.
     */
    @Transactional
    public void upsertHash(int accountId, LocalDate businessDate, String contentHash, 
            int positionCount) {
        String sql = """
            INSERT INTO snapshot_hashes (account_id, business_date, content_hash, position_count, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (account_id, business_date) 
            DO UPDATE SET 
                content_hash = EXCLUDED.content_hash,
                position_count = EXCLUDED.position_count,
                created_at = CURRENT_TIMESTAMP
            """;
        jdbcTemplate.update(sql, accountId, businessDate, contentHash, positionCount);
    }
    
    /**
     * Insert or update hash with additional metrics.
     */
    @Transactional
    public void upsertHashWithMetrics(int accountId, LocalDate businessDate, String contentHash,
            int positionCount, BigDecimal totalQuantity, BigDecimal totalMarketValue) {
        String sql = """
            INSERT INTO snapshot_hashes (
                account_id, business_date, content_hash, position_count, 
                total_quantity, total_market_value, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (account_id, business_date) 
            DO UPDATE SET 
                content_hash = EXCLUDED.content_hash,
                position_count = EXCLUDED.position_count,
                total_quantity = EXCLUDED.total_quantity,
                total_market_value = EXCLUDED.total_market_value,
                created_at = CURRENT_TIMESTAMP
            """;
        jdbcTemplate.update(sql, accountId, businessDate, contentHash, positionCount,
            totalQuantity, totalMarketValue);
    }
    
    /**
     * Delete hash (for reprocessing).
     */
    @Transactional
    public void deleteHash(int accountId, LocalDate businessDate) {
        String sql = "DELETE FROM snapshot_hashes WHERE account_id = ? AND business_date = ?";
        jdbcTemplate.update(sql, accountId, businessDate);
    }
    
    /**
     * Check if hash exists and matches.
     */
    public boolean hashMatches(int accountId, LocalDate businessDate, String contentHash) {
        String sql = """
            SELECT COUNT(*) FROM snapshot_hashes 
            WHERE account_id = ? AND business_date = ? AND content_hash = ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
            accountId, businessDate, contentHash);
        return count != null && count > 0;
    }
    
    /**
     * Get hash info.
     */
    public SnapshotHashInfo getHashInfo(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT content_hash, position_count, total_quantity, total_market_value, created_at
            FROM snapshot_hashes 
            WHERE account_id = ? AND business_date = ?
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return new SnapshotHashInfo(
                    rs.getString("content_hash"),
                    rs.getInt("position_count"),
                    rs.getBigDecimal("total_quantity"),
                    rs.getBigDecimal("total_market_value"),
                    rs.getTimestamp("created_at").toLocalDateTime()
                );
            }
            return null;
        }, accountId, businessDate);
    }
    
    /**
     * Purge old hashes.
     */
    @Transactional
    public int purgeOld(int daysOld) {
        String sql = """
            DELETE FROM snapshot_hashes 
            WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * ?
            """;
        return jdbcTemplate.update(sql, daysOld);
    }
    
    /**
     * Snapshot hash info record.
     */
    public record SnapshotHashInfo(
        String contentHash,
        int positionCount,
        BigDecimal totalQuantity,
        BigDecimal totalMarketValue,
        java.time.LocalDateTime createdAt
    ) {}
}
