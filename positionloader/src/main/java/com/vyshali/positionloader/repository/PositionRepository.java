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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Repository for Position CRUD operations.
 * <p>
 * Uses bitemporal design:
 * - system_from: When the record was created
 * - system_to: When the record was superseded (9999-12-31 = current)
 * <p>
 * Batch design for EOD:
 * - Create new batch in STAGING status
 * - Insert positions into new batch
 * - Activate batch (atomic swap: old ACTIVE → ARCHIVED, new STAGING → ACTIVE)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbc;

    private static final Timestamp END_OF_TIME = Timestamp.valueOf("9999-12-31 23:59:59");

    // ==================== BATCH MANAGEMENT (EOD) ====================

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

        log.debug("Created batch {} for account {}", nextId, accountId);
        return nextId;
    }

    /**
     * Activate a batch (atomic swap).
     */
    public void activateBatch(Integer accountId, int batchId) {
        // Archive old active batch
        jdbc.update("""
                UPDATE Account_Batches 
                SET status = 'ARCHIVED' 
                WHERE account_id = ? AND status = 'ACTIVE'
                """, accountId);

        // Activate new batch
        jdbc.update("""
                UPDATE Account_Batches 
                SET status = 'ACTIVE' 
                WHERE account_id = ? AND batch_id = ?
                """, accountId, batchId);

        log.debug("Activated batch {} for account {}", batchId, accountId);
    }

    /**
     * Cleanup old archived batches.
     */
    public void cleanupBatches(Integer accountId) {
        int deleted = jdbc.update("""
                DELETE FROM Account_Batches 
                WHERE account_id = ? AND status = 'ARCHIVED'
                """, accountId);

        if (deleted > 0) {
            log.debug("Cleaned up {} archived batches for account {}", deleted, accountId);
        }
    }

    // ==================== EOD POSITION INSERT ====================

    /**
     * Insert positions for EOD load.
     */
    public void insertPositions(Integer accountId, List<PositionDTO> positions, String source, int batchId) {
        Timestamp now = Timestamp.from(Instant.now());

        for (PositionDTO p : positions) {
            BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
            BigDecimal cost = p.quantity().multiply(price);

            jdbc.update("""
                    INSERT INTO Positions (
                        account_id, product_id, quantity, avg_cost_price, cost_local,
                        source_system, position_type, batch_id, 
                        system_from, system_to, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 'PHYSICAL', ?, ?, ?, CURRENT_TIMESTAMP)
                    """, accountId, p.productId(), p.quantity(), price, cost, source, batchId, now, END_OF_TIME);
        }

        log.debug("Inserted {} positions for account {} in batch {}", positions.size(), accountId, batchId);
    }

    // ==================== INTRADAY POSITION UPDATE ====================

    /**
     * Update a single position for intraday.
     * Uses bitemporal pattern: close old version, insert new version.
     */
    public void updatePosition(Integer accountId, PositionDTO pos) {
        Timestamp now = Timestamp.from(Instant.now());

        // Close current version
        jdbc.update("""
                UPDATE Positions 
                SET system_to = ?, updated_at = CURRENT_TIMESTAMP
                WHERE account_id = ? AND product_id = ? AND system_to = ?
                """, now, accountId, pos.productId(), END_OF_TIME);

        // Get active batch ID
        Integer activeBatchId = getActiveBatchId(accountId);

        // Insert new version
        BigDecimal price = pos.price() != null ? pos.price() : BigDecimal.ZERO;
        BigDecimal cost = pos.quantity().multiply(price);

        jdbc.update("""
                INSERT INTO Positions (
                    account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, position_type, batch_id,
                    system_from, system_to, updated_at
                )
                VALUES (?, ?, ?, ?, ?, 'INTRADAY', 'PHYSICAL', ?, ?, ?, CURRENT_TIMESTAMP)
                """, accountId, pos.productId(), pos.quantity(), price, cost, activeBatchId != null ? activeBatchId : 0, now, END_OF_TIME);

        log.debug("Updated position: account={}, product={}, qty={}", accountId, pos.productId(), pos.quantity());
    }

    /**
     * Update position quantity by delta (for trade events).
     */
    public void updateQuantity(Integer accountId, Integer productId, BigDecimal delta) {
        BigDecimal current = getQuantity(accountId, productId);
        BigDecimal newQty = current.add(delta);

        Timestamp now = Timestamp.from(Instant.now());

        // Close old version
        jdbc.update("""
                UPDATE Positions 
                SET system_to = ?, updated_at = CURRENT_TIMESTAMP
                WHERE account_id = ? AND product_id = ? AND system_to = ?
                """, now, accountId, productId, END_OF_TIME);

        // Insert new version
        Integer activeBatchId = getActiveBatchId(accountId);

        jdbc.update("""
                INSERT INTO Positions (
                    account_id, product_id, quantity, avg_cost_price, cost_local,
                    source_system, position_type, batch_id,
                    system_from, system_to, updated_at
                )
                VALUES (?, ?, ?, 0, 0, 'INTRADAY', 'PHYSICAL', ?, ?, ?, CURRENT_TIMESTAMP)
                """, accountId, productId, newQty, activeBatchId != null ? activeBatchId : 0, now, END_OF_TIME);
    }

    // ==================== QUERIES ====================

    /**
     * Get current quantity for a position.
     */
    public BigDecimal getQuantity(Integer accountId, Integer productId) {
        try {
            return jdbc.queryForObject("""
                    SELECT quantity FROM Positions
                    WHERE account_id = ? AND product_id = ? AND system_to = ?
                    """, BigDecimal.class, accountId, productId, END_OF_TIME);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
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
     * Count current positions for an account.
     */
    public int countPositions(Integer accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Positions
                WHERE account_id = ? AND system_to = ?
                """, Integer.class, accountId, END_OF_TIME);
        return count != null ? count : 0;
    }

    /**
     * Bitemporal query: Get quantity as of a specific point in time.
     * Used for audit/reconciliation.
     */
    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, Timestamp businessDate, Timestamp systemTime) {
        try {
            return jdbc.queryForObject("""
                    SELECT COALESCE(SUM(quantity), 0) FROM Transactions
                    WHERE account_id = ? AND product_id = ?
                      AND valid_from <= ? AND valid_to > ?
                      AND system_from <= ? AND system_to > ?
                    """, BigDecimal.class, accountId, productId, businessDate, businessDate, systemTime, systemTime);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}