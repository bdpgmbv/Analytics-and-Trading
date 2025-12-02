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

    // EOD: Wipe
    public int deletePositionsByAccount(Integer accountId) {
        return jdbcTemplate.update("DELETE FROM Positions WHERE account_id = ?", accountId);
    }

    // EOD: Insert (Clean)
    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source) {
        String sql = """
                    INSERT INTO Positions (position_id, account_id, product_id, quantity, source_system, position_type)
                    VALUES (nextval('position_seq'), ?, ?, ?, ?, 'PHYSICAL')
                """;
        executeBatch(sql, accountId, positions, source);
    }

    // INTRADAY: Upsert (Requires Unique Index on Account+Product)
    public void batchUpsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source) {
        String sql = """
                    INSERT INTO Positions (account_id, product_id, quantity, source_system, position_type)
                    VALUES (?, ?, ?, ?, 'PHYSICAL')
                    ON CONFLICT (account_id, product_id) 
                    DO UPDATE SET 
                        quantity = EXCLUDED.quantity, 
                        source_system = EXCLUDED.source_system,
                        updated_at = CURRENT_TIMESTAMP
                """;
        executeBatch(sql, accountId, positions, source);
    }

    private void executeBatch(String sql, Integer accountId, List<PositionDetailDTO> positions, String source) {
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                ps.setInt(1, accountId);
                ps.setInt(2, p.getProductId());
                ps.setBigDecimal(3, p.getQuantity());
                ps.setString(4, source);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}
