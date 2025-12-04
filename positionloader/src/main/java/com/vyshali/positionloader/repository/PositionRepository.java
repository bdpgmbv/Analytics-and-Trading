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

    /**
     * EOD Cleanup: Wipes positions for a specific account before reloading.
     */
    public void deletePositionsByAccount(Integer accountId) {
        jdbcTemplate.update("DELETE FROM Positions WHERE account_id = ?", accountId);
    }

    /**
     * EOD Load: Batch Insert (Snapshot).
     * Overwrites state with new Cost/Quantity.
     */
    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source) {
        String sql = """
                    INSERT INTO Positions (
                        position_id, account_id, product_id, 
                        quantity, avg_cost_price, cost_local, 
                        source_system, position_type
                    )
                    VALUES (
                        nextval('position_seq'), ?, ?, 
                        ?, ?, ?, 
                        ?, 'PHYSICAL'
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
                ps.setBigDecimal(4, price);      // avg_cost_price (Initial)
                ps.setBigDecimal(5, totalCost);  // cost_local
                ps.setString(6, source);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    /**
     * INTRADAY Load: Incremental Upsert.
     * Logic:
     * 1. Qty: Add (Buy) or Subtract (Sell).
     * 2. Avg Cost: Update on BUY only (Weighted Average). Keep same on SELL.
     */
    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        String sql = """
                    INSERT INTO Positions (
                        account_id, product_id, quantity, avg_cost_price, cost_local, source_system, position_type
                    )
                    VALUES (
                        ?, ?, 
                        -- 1. Initial Qty (Signed)
                        CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END, 
                        -- 2. Initial Cost (Incoming Price)
                        ?, 
                        -- 3. Initial Total Cost (Qty * Price)
                        (CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END) * ?,
                        ?, 'PHYSICAL'
                    )
                    ON CONFLICT (account_id, product_id) 
                    DO UPDATE SET 
                        -- 4. Calculate New Weighted Average Cost (Only changes on BUY)
                        -- NewWAC = ((CurrentTotalCost) + (TradeQty * TradePrice)) / (CurrentQty + TradeQty)
                        avg_cost_price = CASE 
                            WHEN EXCLUDED.quantity > 0 THEN -- It is a BUY (Positive Qty)
                                ( (Positions.quantity * Positions.avg_cost_price) + (EXCLUDED.quantity * EXCLUDED.avg_cost_price) ) 
                                / NULLIF((Positions.quantity + EXCLUDED.quantity), 0)
                            ELSE Positions.avg_cost_price -- SELLs don't change Avg Cost per share
                        END,
                
                        -- 5. Update Quantity (Add the signed value)
                        quantity = Positions.quantity + EXCLUDED.quantity, 
                
                        -- 6. Recalculate Total Cost based on new Qty and (potentially new) Avg Price
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

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());

                // Params for Quantity Logic
                ps.setString(3, p.txnType());
                ps.setBigDecimal(4, p.quantity());
                ps.setBigDecimal(5, p.quantity());

                // Param for Initial Cost (Avg Price)
                ps.setBigDecimal(6, price);

                // Params for Initial Total Cost Calculation
                ps.setString(7, p.txnType());
                ps.setBigDecimal(8, p.quantity());
                ps.setBigDecimal(9, p.quantity());
                ps.setBigDecimal(10, price);

                // Param for Source
                ps.setString(11, source);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    // ... existing imports ...

    // Add this method to handle incremental updates
    public void upsertPositionQuantity(Integer accountId, Integer productId, BigDecimal quantityDelta) {
        String sql = """
            INSERT INTO Positions (account_id, product_id, quantity, source_system, updated_at)
            VALUES (?, ?, ?, 'INTRADAY', CURRENT_TIMESTAMP)
            ON CONFLICT (account_id, product_id)
            DO UPDATE SET 
                quantity = Positions.quantity + EXCLUDED.quantity,
                updated_at = CURRENT_TIMESTAMP
        """;
        jdbcTemplate.update(sql, accountId, productId, quantityDelta);
    }
}