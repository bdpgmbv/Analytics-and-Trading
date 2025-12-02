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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PositionRepository {
    private final JdbcTemplate jdbcTemplate;

    public void deletePositionsByAccount(Integer accountId) {
        jdbcTemplate.update("DELETE FROM Positions WHERE account_id = ?", accountId);
    }

    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source) {
        String sql = """
                    INSERT INTO Positions (position_id, account_id, product_id, quantity, source_system, position_type)
                    VALUES (nextval('position_seq'), ?, ?, ?, ?, 'PHYSICAL')
                """;
        executeBatch(sql, accountId, positions, source, false);
    }

    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        // ... (SQL remains same as previous step) ...
        String sql = """
                    INSERT INTO Positions (account_id, product_id, quantity, source_system, position_type)
                    VALUES (?, ?, ?, ?, 'PHYSICAL') 
                    ON CONFLICT (account_id, product_id) 
                    DO UPDATE SET 
                        quantity = Positions.quantity + EXCLUDED.quantity, 
                        source_system = EXCLUDED.source_system,
                        updated_at = CURRENT_TIMESTAMP
                """;
        executeBatch(sql, accountId, positions, source, true);
    }

    private void executeBatch(String sql, Integer accountId, List<PositionDetailDTO> positions, String source, boolean applySignLogic) {
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);

                ps.setInt(1, accountId);
                // FIX: .getProductId() -> .productId()
                ps.setInt(2, p.productId());

                // FIX: .getQuantity() -> .quantity()
                java.math.BigDecimal finalQty = p.quantity();

                // FIX: .getTxnType() -> .txnType()
                if (applySignLogic && isNegativeTransaction(p.txnType())) {
                    finalQty = finalQty.negate();
                }

                ps.setBigDecimal(3, finalQty);
                ps.setString(4, source);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    private boolean isNegativeTransaction(String type) {
        return type != null && (type.equalsIgnoreCase("SELL") || type.equalsIgnoreCase("SHORT_SELL") || type.equalsIgnoreCase("DELIVER"));
    }
}