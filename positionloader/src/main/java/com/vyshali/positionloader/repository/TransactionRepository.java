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
        // UPDATED SQL: Added 'ON CONFLICT DO NOTHING' to prevent crashes on duplicates
        String sql = """
            INSERT INTO Transactions (
                transaction_id, account_id, product_id, txn_type, 
                trade_date, quantity, price, cost_local, created_at
            ) VALUES (
                nextval('position_seq'), 
                ?, ?, ?, ?, ?, ?, ?, 
                CURRENT_TIMESTAMP
            )
            ON CONFLICT DO NOTHING 
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
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}