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
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public void batchInsertTransactions(Integer accountId, List<PositionDetailDTO> positions) {
        jdbcTemplate.batchUpdate(TransactionSql.INSERT_TXN, new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                long txnId = System.nanoTime() + i;
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
        try {
            return jdbcTemplate.queryForObject(TransactionSql.FIND_QTY_BY_REF, BigDecimal.class, refId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}