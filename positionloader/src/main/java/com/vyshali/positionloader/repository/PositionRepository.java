package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Refactored for Bitemporal Architecture
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
}