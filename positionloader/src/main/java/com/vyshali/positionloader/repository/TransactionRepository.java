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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * EOD Cleanup: Wipes transactions for a specific account before reloading.
     * This ensures we don't have duplicate EOD snapshots in the history table.
     */
    public void deleteTransactionsByAccount(Integer accountId) {
        jdbcTemplate.update("DELETE FROM Transactions WHERE account_id = ?", accountId);
    }

    /**
     * Bulk inserts position snapshots into the Transaction history table.
     * Used during EOD processing to create an audit trail.
     */
    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        String sql = """
                    INSERT INTO Transactions (
                        transaction_id, 
                        account_id, 
                        product_id, 
                        txn_type, 
                        trade_date, 
                        quantity, 
                        created_at
                    ) VALUES (
                        nextval('position_seq'), -- Using existing sequence or create specific 'txn_seq'
                        ?, 
                        ?, 
                        ?, 
                        ?, 
                        ?, 
                        CURRENT_TIMESTAMP
                    )
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);

                // 1. Account ID
                ps.setInt(1, accountId);

                // 2. Product ID
                ps.setInt(2, p.productId());

                // 3. Transaction Type (Default to 'EOD_SNAPSHOT' if null, or use payload value)
                String type = (p.txnType() != null && !p.txnType().isEmpty()) ? p.txnType() : "EOD_SNAPSHOT";
                ps.setString(3, type);

                // 4. Trade Date (Today)
                ps.setDate(4, Date.valueOf(LocalDate.now()));

                // 5. Quantity
                ps.setBigDecimal(5, p.quantity());
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }
}