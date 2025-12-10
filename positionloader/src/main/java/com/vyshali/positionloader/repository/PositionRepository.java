package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - FIXED: Added missing getQuantityAsOf for bitemporal queries
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    /**
     * Create a new staging batch for an account.
     */
    public int createBatch(Integer accountId) {
        Integer maxId = jdbc.queryForObject("SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?", Integer.class, accountId);

        int nextId = (maxId != null ? maxId : 0) + 1;

        jdbc.update("""
                INSERT INTO Account_Batches (account_id, batch_id, status, created_at)
                VALUES (?, ?, 'STAGING', CURRENT_TIMESTAMP)
                """, accountId, nextId);

        return nextId;
    }

    /**
     * Insert positions into a batch.
     */
    public void insertPositions(Integer accountId, List<PositionDTO> positions, String source, int batchId) {
        for (PositionDTO p : positions) {
            BigDecimal cost = p.quantity().multiply(p.price());

            jdbc.update("""
                    INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local,
                        source_system, batch_id, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, accountId, p.productId(), p.quantity(), p.price(), cost, source, batchId);
        }
    }

    /**
     * Activate a batch (atomic swap: old ACTIVE → ARCHIVED, new STAGING → ACTIVE).
     */
    public void activateBatch(Integer accountId, int batchId) {
        jdbc.update("""
                UPDATE Account_Batches SET status = 'ARCHIVED'
                WHERE account_id = ? AND status = 'ACTIVE'
                """, accountId);

        jdbc.update("""
                UPDATE Account_Batches SET status = 'ACTIVE'
                WHERE account_id = ? AND batch_id = ?
                """, accountId, batchId);
    }

    /**
     * Get active batch ID for an account.
     */
    public Integer getActiveBatchId(Integer accountId) {
        try {
            return jdbc.queryForObject("""
                    SELECT batch_id FROM Account_Batches
                    WHERE account_id = ? AND status = 'ACTIVE'
                    """, Integer.class, accountId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Update a single position (for intraday).
     */
    public void updatePosition(Integer accountId, PositionDTO pos) {
        Integer batchId = getActiveBatchId(accountId);
        if (batchId == null) batchId = 1;

        BigDecimal cost = pos.quantity().multiply(pos.price());

        jdbc.update("""
                INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, batch_id, updated_at)
                VALUES (?, ?, ?, ?, ?, 'INTRADAY', ?, CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, product_id, batch_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, avg_cost_price = EXCLUDED.avg_cost_price,
                    cost_local = EXCLUDED.cost_local, updated_at = CURRENT_TIMESTAMP
                """, accountId, pos.productId(), pos.quantity(), pos.price(), cost, batchId);
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
            // Bitemporal query: What did we know (systemTime) about position on (businessDate)?
            // Uses system_from/system_to for "when did we record this"
            // Uses updated_at as proxy for business validity
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
}