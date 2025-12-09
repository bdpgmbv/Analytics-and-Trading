package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Refactored for Bitemporal Architecture
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.PositionDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbcTemplate;

    // Helper for Legacy Batch ID logic
    public int createNextBatch(Integer accountId) {
        Integer maxId = jdbcTemplate.queryForObject(PositionSql.GET_MAX_BATCH, Integer.class, accountId);
        int nextId = (maxId != null ? maxId : 0) + 1;
        jdbcTemplate.update(PositionSql.CREATE_NEXT_BATCH, accountId, nextId);
        return nextId;
    }

    public void activateBatch(Integer accountId, int batchId) {
        // In Bitemporal, activation is implicit by timestamp, but we keep this for hygiene
        jdbcTemplate.update(PositionSql.ARCHIVE_OLD_BATCHES, accountId);
    }

    public void cleanUpArchivedBatches(Integer accountId) {
        jdbcTemplate.update(PositionSql.CLEANUP_BATCHES, accountId);
    }

    /**
     * Bitemporal Insert: Closes old version, Inserts new version.
     */
    public void batchInsertPositions(Integer accountId, List<PositionDetailDTO> positions, String source, int batchId) {
        Timestamp now = Timestamp.from(Instant.now());

        for (PositionDetailDTO p : positions) {
            BigDecimal price = p.price() != null ? p.price() : BigDecimal.ZERO;
            BigDecimal totalCost = p.quantity().multiply(price);

            // 1. Close Old Version (Soft Delete)
            jdbcTemplate.update(PositionSql.CLOSE_VERSION, now, accountId, p.productId());

            // 2. Insert New Version
            jdbcTemplate.update(PositionSql.INSERT_VERSION, accountId, p.productId(), p.quantity(), price, totalCost, source, batchId, now // system_from
            );
        }
    }

    public void batchIncrementalUpsert(Integer accountId, List<PositionDetailDTO> positions, String source) {
        // Reuse the safe bitemporal logic
        batchInsertPositions(accountId, positions, source, 0);
    }

    /**
     * Intraday Update: Must read current qty, calculate new qty, then do Bitemporal Write.
     */
    public void upsertPositionQuantity(Integer accountId, Integer productId, BigDecimal quantityDelta) {
        Timestamp now = Timestamp.from(Instant.now());

        // 1. Get Current Quantity
        BigDecimal currentQty = BigDecimal.ZERO;
        try {
            currentQty = jdbcTemplate.queryForObject(PositionSql.GET_CURRENT_QUANTITY, BigDecimal.class, accountId, productId);
        } catch (Exception e) {
            // No position exists yet, that's fine
        }

        if (currentQty == null) currentQty = BigDecimal.ZERO;

        // 2. Calculate New
        BigDecimal newQty = currentQty.add(quantityDelta);

        // 3. Close Old
        jdbcTemplate.update(PositionSql.CLOSE_VERSION, now, accountId, productId);

        // 4. Insert New
        jdbcTemplate.update(PositionSql.INSERT_VERSION, accountId, productId, newQty, BigDecimal.ZERO, // Avg cost logic omitted for brevity
                BigDecimal.ZERO, "INTRADAY_UPDATE", 0, now);
    }

    public BigDecimal getPositionQuantity(Integer accountId, Integer productId) {
        try {
            return jdbcTemplate.queryForObject(PositionSql.GET_CURRENT_QUANTITY, BigDecimal.class, accountId, productId);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}