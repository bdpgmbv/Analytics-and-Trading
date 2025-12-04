package com.vyshali.positionloader.repository;

/*
 * 12/02/2025 - 1:32 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public void deleteTransactionsByAccount(Integer accountId) {
        jdbcTemplate.update("DELETE FROM Transactions WHERE account_id = ?", accountId);
    }

    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        // TIER 1 UPGRADE: Explicitly handle duplicate replay with ON CONFLICT
        String sql = """
                    INSERT INTO Transactions (
                        transaction_id, account_id, product_id, txn_type, 
                        trade_date, quantity, price, cost_local, 
                        external_ref_id, created_at
                    ) VALUES (
                        nextval('position_seq'), 
                        ?, ?, ?, ?, ?, ?, ?, ?, 
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (external_ref_id) DO NOTHING 
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
                BigDecimal cost = p.quantity().multiply(price);

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());
                String type = (p.txnType() != null && !p.txnType().isEmpty()) ? p.txnType() : "EOD_LOAD";
                ps.setString(3, type);
                ps.setDate(4, Date.valueOf(LocalDate.now()));
                ps.setBigDecimal(5, p.quantity());
                ps.setBigDecimal(6, price);
                ps.setBigDecimal(7, cost);

                // CRITICAL FIX: Insert the unique ID provided by upstream
                // If this is null (legacy data), we generate a fallback to avoid crash,
                // though real prod should reject nulls.
                String refId = (p.externalRefId() != null) ? p.externalRefId() : "LEGACY-" + System.nanoTime() + "-" + i;
                ps.setString(8, refId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}