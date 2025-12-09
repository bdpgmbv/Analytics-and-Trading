package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Added Idempotency Check
 * FIXED: Corrected column names to match schema (cost_local, external_ref_id)
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
import java.util.concurrent.atomic.AtomicLong;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    // Thread-safe sequence generator for transaction IDs
    private final AtomicLong txnSequence = new AtomicLong(System.currentTimeMillis());

    /**
     * Batch insert transactions from position list
     * <p>
     * FIXED: Using correct column names from schema:
     * - cost_local (not total_amount)
     * - external_ref_id (not reference_id)
     */
    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        jdbcTemplate.batchUpdate(TransactionSql.INSERT_TXN, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);

                // Generate unique transaction ID
                long txnId = txnSequence.incrementAndGet();

                // Calculate cost_local
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
                BigDecimal costLocal = p.quantity().multiply(price);

                // Generate external reference ID for idempotency
                String externalRefId = p.externalRefId() != null ? p.externalRefId() : "EOD-" + accountId + "-" + p.productId() + "-" + LocalDate.now();

                ps.setLong(1, txnId);
                ps.setInt(2, accountId);
                ps.setInt(3, p.productId());
                ps.setString(4, p.txnType() != null ? p.txnType() : "EOD_LOAD");
                ps.setDate(5, java.sql.Date.valueOf(LocalDate.now()));
                ps.setBigDecimal(6, p.quantity());
                ps.setBigDecimal(7, price);
                ps.setBigDecimal(8, costLocal);
                ps.setString(9, externalRefId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    /**
     * Find quantity by external reference ID
     * Used for AMEND operations to find original quantity
     */
    public BigDecimal findQuantityByRefId(String refId) {
        try {
            return jdbcTemplate.queryForObject(TransactionSql.FIND_QTY_BY_REF, BigDecimal.class, refId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Check if transaction already exists (Idempotency Check)
     * Prevents duplicate processing of the same transaction
     */
    public boolean existsByTransactionId(String transactionId) {
        String sql = "SELECT COUNT(1) FROM Transactions WHERE external_ref_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, transactionId);
        return count != null && count > 0;
    }

    /**
     * Check if external reference ID exists
     */
    public boolean existsByExternalRefId(String externalRefId) {
        String sql = "SELECT COUNT(1) FROM Transactions WHERE external_ref_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, externalRefId);
        return count != null && count > 0;
    }
}