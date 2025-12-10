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
 * Repository for Position CRUD with bitemporal support.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbc;

    // ==================== BATCH MANAGEMENT ====================

    public int createBatch(Integer accountId) {
        Integer maxId = jdbc.queryForObject("SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?", Integer.class, accountId);

        int nextId = (maxId != null ? maxId : 0) + 1;

        jdbc.update("INSERT INTO Account_Batches (account_id, batch_id, status, created_at) VALUES (?, ?, 'STAGING', CURRENT_TIMESTAMP)", accountId, nextId);

        return nextId;
    }

    public void activateBatch(Integer accountId, int batchId) {
        // Archive old batches
        jdbc.update("UPDATE Account_Batches SET status = 'ARCHIVED' WHERE account_id = ? AND status = 'ACTIVE'", accountId);
        // Activate new batch
        jdbc.update("UPDATE Account_Batches SET status = 'ACTIVE' WHERE account_id = ? AND batch_id = ?", accountId, batchId);
    }

    public void cleanupBatches(Integer accountId) {
        jdbc.update("DELETE FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED'", accountId);
    }

    // ==================== POSITION WRITES ====================

    public void insertPositions(Integer accountId, List<PositionDTO> positions, String source, int batchId) {
        Timestamp now = Timestamp.from(Instant.now());

        for (PositionDTO p : positions) {
            // Close old version
            jdbc.update("""
                    UPDATE Positions 
                    SET system_to = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE account_id = ? AND product_id = ? AND system_to = '9999-12-31 23:59:59'
                    """, now, accountId, p.productId());

            // Insert new version
            BigDecimal cost = p.quantity().multiply(p.price() != null ? p.price() : BigDecimal.ZERO);

            jdbc.update("""
                    INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local, 
                                           source_system, position_type, batch_id, system_from, system_to, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PHYSICAL', ?, ?, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
                    """, accountId, p.productId(), p.quantity(), p.price(), cost, source, batchId, now);
        }
    }

    public void updateQuantity(Integer accountId, Integer productId, BigDecimal delta) {
        Timestamp now = Timestamp.from(Instant.now());

        // Get current quantity
        BigDecimal current = getQuantity(accountId, productId);
        BigDecimal newQty = current.add(delta);

        // Close old version
        jdbc.update("""
                UPDATE Positions 
                SET system_to = ?, updated_at = CURRENT_TIMESTAMP
                WHERE account_id = ? AND product_id = ? AND system_to = '9999-12-31 23:59:59'
                """, now, accountId, productId);

        // Insert new version
        jdbc.update("""
                INSERT INTO Positions (account_id, product_id, quantity, avg_cost_price, cost_local, 
                                       source_system, position_type, batch_id, system_from, system_to, updated_at)
                VALUES (?, ?, ?, 0, 0, 'INTRADAY', 'PHYSICAL', 0, ?, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
                """, accountId, productId, newQty, now);
    }

    // ==================== POSITION READS ====================

    public BigDecimal getQuantity(Integer accountId, Integer productId) {
        try {
            return jdbc.queryForObject("""
                    SELECT quantity FROM Positions 
                    WHERE account_id = ? AND product_id = ? AND system_to = '9999-12-31 23:59:59'
                    """, BigDecimal.class, accountId, productId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public int countPositions(Integer accountId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Positions 
                WHERE account_id = ? AND system_to = '9999-12-31 23:59:59'
                """, Integer.class, accountId);
        return count != null ? count : 0;
    }

    // ==================== BITEMPORAL QUERY ====================

    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, Timestamp businessDate, Timestamp systemTime) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity), 0) FROM Transactions
                WHERE account_id = ? AND product_id = ?
                  AND valid_from <= ? AND valid_to > ?
                  AND system_from <= ? AND system_to > ?
                """, BigDecimal.class, accountId, productId, businessDate, businessDate, systemTime, systemTime);
    }
}