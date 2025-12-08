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

    public int createNextBatch(Integer accountId) {
        Integer maxId = jdbcTemplate.queryForObject(PositionSql.GET_MAX_BATCH, Integer.class, accountId);
        int nextId = (maxId != null ? maxId : 0) + 1;
        jdbcTemplate.update(PositionSql.CREATE_NEXT_BATCH, accountId, nextId);
        return nextId;
    }

    public void activateBatch(Integer accountId, int batchId) {
        jdbcTemplate.update(PositionSql.ARCHIVE_OLD_BATCHES, accountId);
        jdbcTemplate.update(PositionSql.ACTIVATE_BATCH, accountId, batchId);
    }

    public void cleanUpArchivedBatches(Integer accountId) {
        jdbcTemplate.update(PositionSql.CLEANUP_POSITIONS, accountId, accountId);
        jdbcTemplate.update(PositionSql.CLEANUP_BATCHES, accountId);
    }

    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source, int batchId) {
        jdbcTemplate.batchUpdate(PositionSql.BATCH_INSERT_POSITION, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
                BigDecimal totalCost = p.quantity().multiply(price);

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());
                ps.setBigDecimal(3, p.quantity());
                ps.setBigDecimal(4, price);
                ps.setBigDecimal(5, totalCost);
                ps.setString(6, source);
                ps.setInt(7, batchId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        Integer batchId;
        try {
            batchId = jdbcTemplate.queryForObject(PositionSql.GET_ACTIVE_BATCH_ID, Integer.class, accountId);
        } catch (Exception e) {
            batchId = createNextBatch(accountId);
            activateBatch(accountId, batchId);
        }
        final int activeBatchId = batchId;

        jdbcTemplate.batchUpdate(PositionSql.UPSERT_INTRADAY, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PositionDetailDTO p = positions.get(i);
                BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;

                ps.setInt(1, accountId);
                ps.setInt(2, p.productId());

                // Qty Logic
                ps.setString(3, p.txnType());
                ps.setBigDecimal(4, p.quantity());
                ps.setBigDecimal(5, p.quantity());

                // Cost Logic
                ps.setBigDecimal(6, price);
                ps.setString(7, p.txnType());
                ps.setBigDecimal(8, p.quantity());
                ps.setBigDecimal(9, p.quantity());
                ps.setBigDecimal(10, price);

                ps.setString(11, source);
                ps.setInt(12, activeBatchId);
                ps.setInt(13, activeBatchId);
            }

            @Override
            public int getBatchSize() {
                return positions.size();
            }
        });
    }

    public void upsertPositionQuantity(Integer accountId, Integer productId, BigDecimal quantityDelta) {
        jdbcTemplate.update(PositionSql.UPDATE_QTY_LIFECYCLE, quantityDelta, accountId, productId, accountId);
    }

    public BigDecimal getPositionQuantity(Integer accountId, Integer productId) {
        try {
            return jdbcTemplate.queryForObject(PositionSql.GET_CURRENT_QTY, BigDecimal.class, accountId, productId, accountId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public void deletePositionsByAccount(Integer accountId) {
    }
}