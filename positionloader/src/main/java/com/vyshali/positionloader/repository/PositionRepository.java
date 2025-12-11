package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.dto.PositionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for position operations.
 */
@Repository
public class PositionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final RowMapper<PositionDto> POSITION_MAPPER = (rs, rowNum) -> new PositionDto(
        rs.getLong("position_id"),
        rs.getInt("account_id"),
        rs.getInt("product_id"),
        rs.getDate("business_date").toLocalDate(),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("price"),
        rs.getString("currency"),
        rs.getBigDecimal("market_value_local"),
        rs.getBigDecimal("market_value_base"),
        rs.getBigDecimal("avg_cost_price"),
        rs.getBigDecimal("cost_local"),
        rs.getInt("batch_id"),
        rs.getString("source"),
        rs.getString("position_type"),
        rs.getBoolean("is_excluded")
    );
    
    public PositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Find positions by account and date.
     */
    public List<PositionDto> findByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, currency,
                   market_value_local, market_value_base, avg_cost_price, cost_local,
                   batch_id, source, position_type, is_excluded
            FROM positions
            WHERE account_id = ? AND business_date = ?
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, accountId, businessDate);
    }
    
    /**
     * Find positions by batch.
     */
    public List<PositionDto> findByBatch(int batchId) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, currency,
                   market_value_local, market_value_base, avg_cost_price, cost_local,
                   batch_id, source, position_type, is_excluded
            FROM positions
            WHERE batch_id = ?
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, batchId);
    }
    
    /**
     * Find latest positions for account.
     */
    public List<PositionDto> findLatestByAccount(int accountId) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, currency,
                   market_value_local, market_value_base, avg_cost_price, cost_local,
                   batch_id, source, position_type, is_excluded
            FROM positions
            WHERE account_id = ? AND business_date = (
                SELECT MAX(business_date) FROM positions WHERE account_id = ?
            )
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, accountId, accountId);
    }
    
    /**
     * Batch insert positions.
     */
    @Transactional
    public int batchInsert(List<PositionDto> positions, int batchId) {
        if (positions.isEmpty()) {
            return 0;
        }
        
        String sql = """
            INSERT INTO positions (account_id, product_id, business_date, quantity, price, currency,
                market_value_local, market_value_base, avg_cost_price, cost_local,
                batch_id, source, position_type, is_excluded)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        List<Object[]> batchArgs = positions.stream()
            .map(p -> new Object[]{
                p.accountId(),
                p.productId(),
                p.businessDate(),
                p.quantity(),
                p.price(),
                p.currency(),
                p.marketValueLocal(),
                p.marketValueBase(),
                p.avgCostPrice(),
                p.costLocal(),
                batchId,
                p.source(),
                p.positionType(),
                p.isExcluded()
            })
            .toList();
        
        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);
        
        int total = 0;
        for (int r : results) {
            if (r > 0) total += r;
            else if (r == -2) total++; // SUCCESS_NO_INFO
        }
        return total;
    }
    
    /**
     * Delete positions by account and date.
     */
    @Transactional
    public int deleteByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = "DELETE FROM positions WHERE account_id = ? AND business_date = ?";
        return jdbcTemplate.update(sql, accountId, businessDate);
    }
    
    /**
     * Delete positions by batch.
     */
    @Transactional
    public int deleteByBatch(int batchId) {
        String sql = "DELETE FROM positions WHERE batch_id = ?";
        return jdbcTemplate.update(sql, batchId);
    }
    
    /**
     * Count positions by account and date.
     */
    public int countByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = "SELECT COUNT(*) FROM positions WHERE account_id = ? AND business_date = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, businessDate);
        return count != null ? count : 0;
    }
    
    /**
     * Get total market value for account and date.
     */
    public BigDecimal getTotalMarketValue(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT COALESCE(SUM(market_value_base), 0) 
            FROM positions 
            WHERE account_id = ? AND business_date = ?
            """;
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId, businessDate);
    }
    
    /**
     * Find position by ID.
     */
    public PositionDto findById(long positionId) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, currency,
                   market_value_local, market_value_base, avg_cost_price, cost_local,
                   batch_id, source, position_type, is_excluded
            FROM positions
            WHERE position_id = ?
            """;
        List<PositionDto> results = jdbcTemplate.query(sql, POSITION_MAPPER, positionId);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Update position quantity.
     */
    @Transactional
    public void updateQuantity(long positionId, BigDecimal newQuantity) {
        String sql = """
            UPDATE positions 
            SET quantity = ?, 
                market_value_local = quantity * price,
                market_value_base = quantity * price
            WHERE position_id = ?
            """;
        jdbcTemplate.update(sql, newQuantity, positionId);
    }
    
    /**
     * Mark position as excluded.
     */
    @Transactional
    public void setExcluded(long positionId, boolean excluded) {
        String sql = "UPDATE positions SET is_excluded = ? WHERE position_id = ?";
        jdbcTemplate.update(sql, excluded, positionId);
    }
    
    /**
     * Find positions by product.
     */
    public List<PositionDto> findByProduct(int productId, LocalDate businessDate) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, currency,
                   market_value_local, market_value_base, avg_cost_price, cost_local,
                   batch_id, source, position_type, is_excluded
            FROM positions
            WHERE product_id = ? AND business_date = ?
            ORDER BY account_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, productId, businessDate);
    }
}
