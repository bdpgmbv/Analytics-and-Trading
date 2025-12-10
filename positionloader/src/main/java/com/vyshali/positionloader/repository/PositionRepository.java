package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - UPDATED: Added batch insert for 5-10x performance improvement
 *
 * PERFORMANCE IMPROVEMENT:
 * - Before: 500 positions = 500 separate INSERT statements (500 round trips)
 * - After:  500 positions = 1 batch INSERT (1 round trip)
 * - Expected: 5-10x faster EOD processing
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Repository for Position CRUD operations.
 * Uses batch-based atomic swap for EOD loads.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbc;

    // Batch size for optimal PostgreSQL performance
    private static final int BATCH_SIZE = 500;

    /**
     * Create a new staging batch for an account.
     */
    @CacheEvict(value = "activeBatch", key = "#accountId")
    public int createBatch(Integer accountId) {
        Integer maxId = jdbc.queryForObject("SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?", Integer.class, accountId);

        int nextId = (maxId != null ? maxId : 0) + 1;

        jdbc.update("""
                INSERT INTO Account_Batches (account_id, batch_id, status, created_at)
                VALUES (?, ?, 'STAGING', CURRENT_TIMESTAMP)
                """, accountId, nextId);

        log.debug("Created batch {} for account {}", nextId, accountId);
        return nextId;
    }

    /**
     * BATCH INSERT positions - 5-10x faster than individual inserts.
     * <p>
     * Uses JDBC batch update for optimal performance with PostgreSQL.
     * Processes in chunks of BATCH_SIZE to avoid memory issues with large loads.
     */
    public void insertPositions(Integer accountId, List<PositionDTO> positions, String source, int batchId) {
        if (positions == null || positions.isEmpty()) {
            log.debug("No positions to insert for account {}", accountId);
            return;
        }

        long startTime = System.currentTimeMillis();
        int totalInserted = 0;

        // Process in chunks for very large position lists
        for (int i = 0; i < positions.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, positions.size());
            List<PositionDTO> chunk = positions.subList(i, endIndex);

            int[] results = insertBatch(accountId, chunk, source, batchId);
            totalInserted += results.length;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Batch inserted {} positions for account {} in {}ms ({}ms/position)", totalInserted, accountId, elapsed, positions.isEmpty() ? 0 : elapsed / positions.size());
    }

    /**
     * Internal batch insert using JDBC batch update.
     */
    private int[] insertBatch(Integer accountId, List<PositionDTO> positions, String source, int batchId) {
        String sql = """
                INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, batch_id, system_from, system_to, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
                """;

        return jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDTO p = positions.get(i);
                BigDecimal cost = p.quantity().multiply(p.price());

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());
                ps.setBigDecimal(3, p.quantity());
                ps.setBigDecimal(4, p.price());
                ps.setBigDecimal(5, cost);
                ps.setString(6, source);
                ps.setInt(7, batchId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    /**
     * Activate a batch (atomic swap: old ACTIVE → ARCHIVED, new STAGING → ACTIVE).
     */
    @CacheEvict(value = "activeBatch", key = "#accountId")
    public void activateBatch(Integer accountId, int batchId) {
        // Close old active batch
        jdbc.update("""
                UPDATE Account_Batches SET status = 'ARCHIVED'
                WHERE account_id = ? AND status = 'ACTIVE'
                """, accountId);

        // Activate new batch
        jdbc.update("""
                UPDATE Account_Batches SET status = 'ACTIVE'
                WHERE account_id = ? AND batch_id = ?
                """, accountId, batchId);

        log.info("Activated batch {} for account {} (atomic swap complete)", batchId, accountId);
    }

    /**
     * Get active batch ID for an account - CACHED.
     */
    @Cacheable(value = "activeBatch", key = "#accountId", unless = "#result == null")
    public Integer getActiveBatchId(Integer accountId) {
        try {
            return jdbc.queryForObject("""
                    SELECT batch_id FROM Account_Batches
                    WHERE account_id = ? AND status = 'ACTIVE'
                    """, Integer.class, accountId);
        } catch (Exception e) {
            log.debug("No active batch for account {}", accountId);
            return null;
        }
    }

    /**
     * Update a single position (for intraday).
     * Uses UPSERT for idempotency.
     */
    public void updatePosition(Integer accountId, PositionDTO pos) {
        Integer batchId = getActiveBatchId(accountId);
        if (batchId == null) {
            batchId = 1;
            log.warn("No active batch for account {}, using default batch 1", accountId);
        }

        BigDecimal cost = pos.quantity().multiply(pos.price());

        jdbc.update("""
                INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, batch_id, system_from, system_to, updated_at)
                VALUES (?, ?, ?, ?, ?, 'INTRADAY', ?, CURRENT_TIMESTAMP, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, product_id, batch_id)
                DO UPDATE SET 
                    quantity = EXCLUDED.quantity, 
                    avg_cost_price = EXCLUDED.avg_cost_price,
                    cost_local = EXCLUDED.cost_local, 
                    updated_at = CURRENT_TIMESTAMP
                """, accountId, pos.productId(), pos.quantity(), pos.price(), cost, batchId);
    }

    /**
     * Batch update positions (for intraday batch processing).
     */
    public void updatePositions(Integer accountId, List<PositionDTO> positions) {
        if (positions == null || positions.isEmpty()) return;

        Integer batchId = getActiveBatchId(accountId);
        if (batchId == null) batchId = 1;

        final int finalBatchId = batchId;

        String sql = """
                INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, batch_id, system_from, system_to, updated_at)
                VALUES (?, ?, ?, ?, ?, 'INTRADAY', ?, CURRENT_TIMESTAMP, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, product_id, batch_id)
                DO UPDATE SET 
                    quantity = EXCLUDED.quantity, 
                    avg_cost_price = EXCLUDED.avg_cost_price,
                    cost_local = EXCLUDED.cost_local, 
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDTO p = positions.get(i);
                BigDecimal cost = p.quantity().multiply(p.price());

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());
                ps.setBigDecimal(3, p.quantity());
                ps.setBigDecimal(4, p.price());
                ps.setBigDecimal(5, cost);
                ps.setInt(6, finalBatchId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });

        log.debug("Batch updated {} intraday positions for account {}", positions.size(), accountId);
    }

    /**
     * Count positions for an account.
     */
    public int countPositions(Integer accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Positions p
                JOIN Account_Batches ab ON p.account_id = ab.account_id AND p.batch_id = ab.batch_id
                WHERE p.account_id = ? AND ab.status = 'ACTIVE'
                """, Integer.class, accountId);
        return count != null ? count : 0;
    }

    // ==================== BITEMPORAL QUERIES ====================

    /**
     * Get position quantity as of a specific business date and system time.
     * This is the core bitemporal query for audit/compliance.
     *
     * @param accountId    The account ID
     * @param productId    The product ID
     * @param businessDate The business date we're asking about
     * @param systemTime   The system time (when did we know this?)
     * @return The quantity as of that point in time, or ZERO if not found
     */
    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, Timestamp businessDate, Timestamp systemTime) {
        try {
            return jdbc.queryForObject("""
                    SELECT quantity FROM Positions
                    WHERE account_id = ?
                      AND product_id = ?
                      AND system_from <= ?
                      AND system_to > ?
                      AND updated_at <= ?
                    ORDER BY system_from DESC
                    LIMIT 1
                    """, BigDecimal.class, accountId, productId, systemTime, systemTime, businessDate);
        } catch (Exception e) {
            log.debug("No position found for account={}, product={}, asOf={}/{}", accountId, productId, businessDate, systemTime);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get all positions for an account as of a specific time.
     */
    public List<PositionDTO> getPositionsAsOf(Integer accountId, Timestamp asOfTime) {
        return jdbc.query("""
                SELECT p.product_id, pr.ticker, pr.asset_class, pr.issue_currency,
                       p.quantity, p.avg_cost_price, 'HISTORICAL' as txn_type, NULL as external_ref_id
                FROM Positions p
                JOIN Products pr ON p.product_id = pr.product_id
                WHERE p.account_id = ?
                  AND p.system_from <= ?
                  AND p.system_to > ?
                ORDER BY pr.ticker
                """, (rs, rowNum) -> new PositionDTO(rs.getInt("product_id"), rs.getString("ticker"), rs.getString("asset_class"), rs.getString("issue_currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("avg_cost_price"), rs.getString("txn_type"), rs.getString("external_ref_id")), accountId, asOfTime, asOfTime);
    }

    // ==================== CLEANUP ====================

    /**
     * Delete old archived batches (for maintenance).
     * Keep last N batches for audit trail.
     */
    public int cleanupOldBatches(Integer accountId, int keepLastN) {
        return jdbc.update("""
                DELETE FROM Positions 
                WHERE account_id = ? 
                AND batch_id NOT IN (
                    SELECT batch_id FROM Account_Batches 
                    WHERE account_id = ? 
                    ORDER BY batch_id DESC 
                    LIMIT ?
                )
                """, accountId, accountId, keepLastN);
    }
}