package com.vyshali.positionloader.repository;

/*
 * 12/1/25 - 22:59
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbcTemplate;

    // ==================================================================================
    // 1. BLUE/GREEN BATCH MANAGEMENT (Snapshot Isolation)
    // ==================================================================================

    /**
     * Creates a new "STAGING" batch. This batch is invisible to the UI/Downstream
     * until activated.
     * Use Case: Start of Day (SOD) or End of Day (EOD) loading.
     */
    public int createNextBatch(Integer accountId) {
        // 1. Find the highest batch ID currently used (or 0 if none)
        Integer maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?",
                Integer.class, accountId);
        int nextId = maxId + 1;

        // 2. Create the new batch in 'STAGING' mode
        jdbcTemplate.update(
                "INSERT INTO Account_Batches (account_id, batch_id, status) VALUES (?, ?, 'STAGING')",
                accountId, nextId);

        return nextId;
    }

    /**
     * Atomically switches the "ACTIVE" batch. This is the "Flip".
     * Use Case: After EOD load is successfully committed.
     */
    public void activateBatch(Integer accountId, int batchId) {
        // 1. Mark the OLD active batch as ARCHIVED
        jdbcTemplate.update(
                "UPDATE Account_Batches SET status = 'ARCHIVED' WHERE account_id = ? AND status = 'ACTIVE'",
                accountId);

        // 2. Mark the NEW batch as ACTIVE
        jdbcTemplate.update(
                "UPDATE Account_Batches SET status = 'ACTIVE' WHERE account_id = ? AND batch_id = ?",
                accountId, batchId);
    }

    /**
     * Deletes old archived data to save space.
     * Use Case: Housekeeping after EOD.
     */
    public void cleanUpArchivedBatches(Integer accountId) {
        // 1. Delete actual position rows
        String sql = """
            DELETE FROM Positions 
            WHERE account_id = ? 
              AND batch_id IN (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED')
        """;
        jdbcTemplate.update(sql, accountId, accountId);

        // 2. Delete metadata rows
        jdbcTemplate.update("DELETE FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED'", accountId);
    }

    // ==================================================================================
    // 2. WRITE OPERATIONS (Insert / Update)
    // ==================================================================================

    /**
     * Batch Insert for EOD Load. Writes to a specific (Staging) Batch ID.
     */
    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source, int batchId) {
        String sql = """
            INSERT INTO Positions (
                position_id, account_id, product_id, 
                quantity, avg_cost_price, cost_local, 
                source_system, position_type, batch_id
            )
            VALUES (
                nextval('position_seq'), ?, ?, 
                ?, ?, ?, 
                ?, 'PHYSICAL', ?
            )
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
                BigDecimal totalCost = p.quantity().multiply(price);

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());
                ps.setBigDecimal(3, p.quantity());
                ps.setBigDecimal(4, price);
                ps.setBigDecimal(5, totalCost);
                ps.setString(6, source);
                ps.setInt(7, batchId);
            }

            @Override
            public int getBatchSize() { return positions.size(); }
        });
    }

    /**
     * Incremental Upsert for Intraday Trading.
     * Automatically finds and updates the currently ACTIVE batch.
     * Calculates Weighted Average Cost (WAC) on the fly.
     */
    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        // 1. Find the Active Batch (Fail if none exists)
        Integer batchId;
        try {
            batchId = jdbcTemplate.queryForObject(
                    "SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE'",
                    Integer.class, accountId);
        } catch (Exception e) {
            // Self-Healing: If no batch exists (e.g. new account), create one.
            batchId = createNextBatch(accountId);
            activateBatch(accountId, batchId);
        }
        final int activeBatchId = batchId;

        // 2. Upsert Logic (Postgres Specific)
        // Requires UNIQUE INDEX on (account_id, product_id, batch_id)
        String sql = """
            INSERT INTO Positions (
                account_id, product_id, quantity, avg_cost_price, cost_local, source_system, position_type, batch_id
            )
            VALUES (
                ?, ?, 
                CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END, 
                ?, 
                (CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END) * ?,
                ?, 'PHYSICAL', ?
            )
            ON CONFLICT (account_id, product_id) WHERE batch_id = ? 
            DO UPDATE SET 
                -- Weighted Average Cost Formula: (OldCost + NewCost) / (OldQty + NewQty)
                avg_cost_price = CASE 
                    WHEN (Positions.quantity + EXCLUDED.quantity) <> 0 THEN 
                        ( (Positions.quantity * Positions.avg_cost_price) + (EXCLUDED.quantity * EXCLUDED.avg_cost_price) ) 
                        / (Positions.quantity + EXCLUDED.quantity)
                    ELSE Positions.avg_cost_price 
                END,
                quantity = Positions.quantity + EXCLUDED.quantity, 
                cost_local = Positions.cost_local + EXCLUDED.cost_local,
                source_system = EXCLUDED.source_system,
                updated_at = CURRENT_TIMESTAMP
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());

                // Qty (Column 3, 4, 5)
                ps.setString(3, p.txnType());
                ps.setBigDecimal(4, p.quantity());
                ps.setBigDecimal(5, p.quantity());

                // Price (Column 6)
                ps.setBigDecimal(6, price);

                // Cost Calculation (Column 7, 8, 9)
                ps.setString(7, p.txnType());
                ps.setBigDecimal(8, p.quantity());
                ps.setBigDecimal(9, p.quantity());
                ps.setBigDecimal(10, price);

                ps.setString(11, source);
                ps.setInt(12, activeBatchId);
                ps.setInt(13, activeBatchId); // For WHERE clause
            }

            @Override
            public int getBatchSize() { return positions.size(); }
        });
    }

    /**
     * Updates specific position quantity. Used for Amends/Cancels/Sales.
     * Targets the ACTIVE batch.
     */
    public void upsertPositionQuantity(Integer accountId, Integer productId, BigDecimal quantityDelta) {
        String sql = """
            UPDATE Positions 
            SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP
            WHERE account_id = ? 
              AND product_id = ? 
              AND batch_id = (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE')
        """;
        jdbcTemplate.update(sql, quantityDelta, accountId, productId, accountId);
    }

    // ==================================================================================
    // 3. READ OPERATIONS (Queries)
    // ==================================================================================

    /**
     * Fetches the current total quantity for a product in the ACTIVE batch.
     * Used to publish granular updates to Price Service.
     */
    public BigDecimal getPositionQuantity(Integer accountId, Integer productId) {
        String sql = """
            SELECT COALESCE(SUM(quantity), 0)
            FROM Positions 
            WHERE account_id = ? 
              AND product_id = ? 
              AND batch_id = (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE')
        """;
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId, productId, accountId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Legacy Delete (No-op in Blue/Green as we use cleanUpArchivedBatches)
     */
    public void deletePositionsByAccount(Integer accountId) {
        // Intentionally left empty or deprecated
    }
}