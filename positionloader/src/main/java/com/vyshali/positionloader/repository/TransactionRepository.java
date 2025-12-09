package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Added Idempotency Check
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
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        // Assuming TransactionSql.INSERT_TXN is defined elsewhere or inlined
        String sql = "INSERT INTO Transactions (transaction_id, account_id, product_id, txn_type, trade_date, quantity, price, total_amount, reference_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                long txnId = System.nanoTime() + i; // Ideally use a sequence or UUID
                ps.setLong(1, txnId);
                ps.setInt(2, accountId);
                ps.setInt(3, p.productId());
                ps.setString(4, "EOD_LOAD");
                ps.setDate(5, java.sql.Date.valueOf(LocalDate.now()));
                ps.setBigDecimal(6, p.quantity());
                ps.setBigDecimal(7, p.price());
                ps.setBigDecimal(8, p.quantity().multiply(p.price() != null ? p.price() : BigDecimal.ZERO));
                ps.setString(9, "EOD-" + accountId + "-" + p.productId() + "-" + LocalDate.now());
            }

            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    public BigDecimal findQuantityByRefId(String refId) {
        String sql = "SELECT quantity FROM Transactions WHERE reference_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, refId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // *** NEW METHOD FOR IDEMPOTENCY ***
    public boolean existsByTransactionId(String transactionId) {
        String sql = "SELECT COUNT(1) FROM Transactions WHERE reference_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, transactionId);
        return count != null && count > 0;
    }
}