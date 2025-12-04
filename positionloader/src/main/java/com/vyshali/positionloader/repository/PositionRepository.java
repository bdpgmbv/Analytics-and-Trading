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
    // 1. BLUE/GREEN BATCH MANAGEMENT
    // ==================================================================================

    public int createNextBatch(Integer accountId) {
        // Find the highest batch ID currently used (or 0 if none)
        Integer maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?", Integer.class, accountId);
        int nextId = maxId + 1;

        // Create the new batch in 'STAGING' mode (invisible to UI)
        jdbcTemplate.update("INSERT INTO Account_Batches (account_id, batch_id, status) VALUES (?, ?, 'STAGING')", accountId, nextId);

        return nextId;
    }

    public void activateBatch(Integer accountId, int batchId) {
        // 1. Archive the currently ACTIVE batch
        jdbcTemplate.update("UPDATE Account_Batches SET status = 'ARCHIVED' WHERE account_id = ? AND status = 'ACTIVE'", accountId);

        // 2. Flip the STAGING batch to ACTIVE
        jdbcTemplate.update("UPDATE Account_Batches SET status = 'ACTIVE' WHERE account_id = ? AND batch_id = ?", accountId, batchId);
    }

    public void cleanUpArchivedBatches(Integer accountId) {
        // 1. Delete actual position rows from old batches
        String sql = """
                    DELETE FROM Positions 
                    WHERE account_id = ? 
                      AND batch_id IN (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED')
                """;
        jdbcTemplate.update(sql, accountId, accountId);

        // 2. Delete the batch metadata records
        jdbcTemplate.update("DELETE FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED'", accountId);
    }

    // ==================================================================================
    // 2. WRITE OPERATIONS
    // ==================================================================================

    /**
     * EOD Load: Inserts into a specific (Staging) batch.
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
                ps.setInt(7, batchId); // Insert into the specific batch
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    /**
     * Intraday Load: Incremental Upsert on the ACTIVE batch.
     */
    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        // 1. Resolve the currently Active Batch
        Integer activeBatchId;
        try {
            activeBatchId = jdbcTemplate.queryForObject("SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE'", Integer.class, accountId);
        } catch (Exception e) {
            // Fallback: If no batch exists, create one (auto-healing)
            activeBatchId = createNextBatch(accountId);
            activateBatch(accountId, activeBatchId);
        }
        final int batchId = activeBatchId;

        // 2. Perform Upsert
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
                    ON CONFLICT (account_id, product_id) WHERE batch_id = ?  -- Partial Index Conflict (Postgres Specific)
                    DO UPDATE SET 
                        avg_cost_price = CASE 
                            WHEN EXCLUDED.quantity > 0 THEN 
                                ( (Positions.quantity * Positions.avg_cost_price) + (EXCLUDED.quantity * EXCLUDED.avg_cost_price) ) 
                                / NULLIF((Positions.quantity + EXCLUDED.quantity), 0)
                            ELSE Positions.avg_cost_price 
                        END,
                        quantity = Positions.quantity + EXCLUDED.quantity, 
                        cost_local = (Positions.quantity + EXCLUDED.quantity) * (
                            CASE 
                                WHEN EXCLUDED.quantity > 0 THEN 
                                   ((Positions.quantity * Positions.avg_cost_price) + (EXCLUDED.quantity * EXCLUDED.avg_cost_price)) 
                                   / NULLIF((Positions.quantity + EXCLUDED.quantity), 0)
                                ELSE Positions.avg_cost_price 
                            END
                        ),
                        source_system = EXCLUDED.source_system,
                        updated_at = CURRENT_TIMESTAMP
                """;

        // Note: The ON CONFLICT clause above assumes you have a unique constraint/index on (account_id, product_id, batch_id).
        // Since we added batch_id to the table, we should ideally drop the old constraint and add a new one including batch_id.
        // For this code to work strictly as written, ensure you run:
        // DROP INDEX uq_pos_account_product;
        // CREATE UNIQUE INDEX uq_pos_batch_prod ON Positions(account_id, product_id, batch_id);

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());

                // Qty Logic
                ps.setString(3, p.txnType());
                ps.setBigDecimal(4, p.quantity());
                ps.setBigDecimal(5, p.quantity());

                // Cost Logic
                ps.setBigDecimal(6, price);
                ps.setString(7, p.txnType());
                ps.setBigDecimal(8, p.quantity());
                ps.setBigDecimal(9, p.quantity());
                ps.setBigDecimal(10, price);

                ps.setString(11, source);
                ps.setInt(12, batchId);
                ps.setInt(13, batchId); // For ON CONFLICT WHERE clause
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    /**
     * Updates quantity for Trade Lifecycle (Amend/Cancel). Target ACTIVE batch.
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

    // Legacy support (No-op in Blue/Green as we use cleanUpArchivedBatches)
    public void deletePositionsByAccount(Integer accountId) {
    }
}