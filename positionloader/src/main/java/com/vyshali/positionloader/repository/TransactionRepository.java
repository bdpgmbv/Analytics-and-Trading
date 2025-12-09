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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        jdbcTemplate.batchUpdate(TransactionSql.INSERT_TXN, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                // Simple hash for ID generation if not provided (demo logic)
                long txnId = System.nanoTime() + i;

                ps.setLong(1, txnId);
                ps.setInt(2, accountId);
                ps.setInt(3, p.productId());
                ps.setString(4, "OPENING_LOAD"); // Default type for EOD
                ps.setDate(5, java.sql.Date.valueOf(java.time.LocalDate.now()));
                ps.setBigDecimal(6, p.quantity());
                ps.setBigDecimal(7, p.price());
                ps.setBigDecimal(8, p.quantity().multiply(p.price() != null ? p.price() : BigDecimal.ZERO));
                ps.setString(9, "MSPM-EOD-" + accountId + "-" + p.productId()); // Dedup key
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    public BigDecimal findQuantityByRefId(String refId) {
        try {
            return jdbcTemplate.queryForObject(TransactionSql.FIND_QTY_BY_REF, BigDecimal.class, refId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}