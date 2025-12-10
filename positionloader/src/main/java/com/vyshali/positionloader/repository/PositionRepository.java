package com.vyshali.positionloader.repository;

/*
 * SIMPLIFIED: Removed redundant method overloads
 *
 * BEFORE: Multiple overloads "for test compatibility"
 *   - insertPositions(accountId, positions, source, batchId)
 *   - insertPositions(positions, batchId, businessDate)  // "test compat"
 *   - getQuantityAsOf(accountId, productId, Timestamp, Timestamp)
 *   - getQuantityAsOf(accountId, productId, LocalDateTime)  // "test compat"
 *   - getPositionsAsOf(accountId, Timestamp)
 *   - getPositionsAsOf(accountId, LocalDate)  // "test compat"
 *
 * AFTER: Single signature per operation
 *   - Tests updated to use the same signatures as production code
 *
 * PRINCIPLE: Tests should use production APIs, not special "test" APIs
 */

import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PositionRepository {

    private final JdbcTemplate jdbcTemplate;

    // ═══════════════════════════════════════════════════════════════════════════
    // INSERT OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Insert positions for an account.
     *
     * @param accountId    Account ID
     * @param positions    List of positions to insert
     * @param source       Source system (MSPM, UPLOAD, etc.)
     * @param batchId      Batch ID for this insert
     * @param businessDate Business date for the positions
     * @return Number of positions inserted
     */
    @Transactional
    public int insertPositions(Integer accountId, List<PositionDTO> positions, String source, int batchId, LocalDate businessDate) {

        if (positions == null || positions.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO positions (
                    account_id, product_id, quantity, price, currency,
                    business_date, batch_id, source, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (account_id, product_id, business_date) 
                DO UPDATE SET 
                    quantity = EXCLUDED.quantity,
                    price = EXCLUDED.price,
                    batch_id = EXCLUDED.batch_id,
                    updated_at = NOW()
                """;

        int count = 0;
        for (PositionDTO pos : positions) {
            jdbcTemplate.update(sql, accountId, pos.productId(), pos.quantity(), pos.price(), pos.currency(), businessDate, batchId, source);
            count++;
        }

        log.debug("Inserted {} positions for account {} batch {}", count, accountId, batchId);
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get quantity for a specific position as of a point in time.
     *
     * @param accountId Account ID
     * @param productId Product ID
     * @param asOfTime  Point in time to query
     * @return Quantity at that time, or null if not found
     */
    public BigDecimal getQuantityAsOf(Integer accountId, String productId, LocalDateTime asOfTime) {
        String sql = """
                SELECT quantity 
                FROM positions 
                WHERE account_id = ? 
                  AND product_id = ? 
                  AND created_at <= ?
                ORDER BY created_at DESC 
                LIMIT 1
                """;

        List<BigDecimal> results = jdbcTemplate.queryForList(sql, BigDecimal.class, accountId, productId, Timestamp.valueOf(asOfTime));

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get all positions for an account as of a point in time.
     *
     * @param accountId Account ID
     * @param asOfTime  Point in time to query
     * @return List of positions at that time
     */
    public List<PositionDTO> getPositionsAsOf(Integer accountId, LocalDateTime asOfTime) {
        String sql = """
                SELECT DISTINCT ON (product_id)
                    product_id, quantity, price, currency
                FROM positions
                WHERE account_id = ?
                  AND created_at <= ?
                ORDER BY product_id, created_at DESC
                """;

        return jdbcTemplate.query(sql, new PositionRowMapper(), accountId, Timestamp.valueOf(asOfTime));
    }

    /**
     * Get current positions for an account (latest for each product).
     *
     * @param accountId Account ID
     * @return List of current positions
     */
    public List<PositionDTO> getCurrentPositions(Integer accountId) {
        return getPositionsAsOf(accountId, LocalDateTime.now());
    }

    /**
     * Get positions for a specific business date.
     *
     * @param accountId    Account ID
     * @param businessDate Business date
     * @return List of positions for that date
     */
    public List<PositionDTO> getPositionsByDate(Integer accountId, LocalDate businessDate) {
        String sql = """
                SELECT product_id, quantity, price, currency
                FROM positions
                WHERE account_id = ?
                  AND business_date = ?
                """;

        return jdbcTemplate.query(sql, new PositionRowMapper(), accountId, businessDate);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get the next batch ID for an account.
     */
    public int getNextBatchId(Integer accountId) {
        String sql = "SELECT COALESCE(MAX(batch_id), 0) + 1 FROM positions WHERE account_id = ?";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, accountId);
        return result != null ? result : 1;
    }

    /**
     * Delete positions for a specific batch.
     */
    @Transactional
    public int deleteByBatch(Integer accountId, int batchId) {
        String sql = "DELETE FROM positions WHERE account_id = ? AND batch_id = ?";
        return jdbcTemplate.update(sql, accountId, batchId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROW MAPPER
    // ═══════════════════════════════════════════════════════════════════════════

    private static class PositionRowMapper implements RowMapper<PositionDTO> {
        @Override
        public PositionDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PositionDTO(rs.getString("product_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("price"), rs.getString("currency"));
        }
    }
}