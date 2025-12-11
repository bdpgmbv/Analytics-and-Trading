package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.dto.PositionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Account_Positions table operations.
 */
@Repository
public class PositionRepository {
    
    private static final Logger log = LoggerFactory.getLogger(PositionRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final PositionRowMapper rowMapper = new PositionRowMapper();
    
    public PositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Find positions by account and date.
     */
    public List<PositionDto> findByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, 
                   currency, market_value_local, market_value_base, avg_cost_price, 
                   cost_local, batch_id, source, position_type, is_excluded
            FROM account_positions 
            WHERE account_id = ? AND business_date = ?
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, rowMapper, accountId, businessDate);
    }
    
    /**
     * Find latest positions for account.
     */
    public List<PositionDto> findLatestByAccount(int accountId) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, 
                   currency, market_value_local, market_value_base, avg_cost_price, 
                   cost_local, batch_id, source, position_type, is_excluded
            FROM account_positions 
            WHERE account_id = ? 
            AND business_date = (
                SELECT MAX(business_date) FROM account_positions WHERE account_id = ?
            )
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, rowMapper, accountId, accountId);
    }
    
    /**
     * Find positions by batch ID.
     */
    public List<PositionDto> findByBatch(int batchId) {
        String sql = """
            SELECT position_id, account_id, product_id, business_date, quantity, price, 
                   currency, market_value_local, market_value_base, avg_cost_price, 
                   cost_local, batch_id, source, position_type, is_excluded
            FROM account_positions 
            WHERE batch_id = ?
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, rowMapper, batchId);
    }
    
    /**
     * Batch insert positions.
     */
    @Transactional
    public int batchInsert(List<PositionDto> positions, int batchId) {
        if (positions == null || positions.isEmpty()) {
            return 0;
        }
        
        String sql = """
            INSERT INTO account_positions (
                account_id, product_id, business_date, quantity, price, currency,
                market_value_local, market_value_base, avg_cost_price, cost_local,
                batch_id, source, position_type, is_excluded
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        int[][] results = jdbcTemplate.batchUpdate(sql, positions, 1000, (ps, position) -> {
            ps.setInt(1, position.accountId());
            ps.setInt(2, position.productId());
            ps.setObject(3, position.businessDate());
            ps.setBigDecimal(4, position.quantity());
            ps.setBigDecimal(5, position.price());
            ps.setString(6, position.currency());
            ps.setBigDecimal(7, position.marketValueLocal());
            ps.setBigDecimal(8, position.marketValueBase());
            ps.setBigDecimal(9, position.avgCostPrice());
            ps.setBigDecimal(10, position.costLocal());
            ps.setInt(11, batchId);
            ps.setString(12, position.source());
            ps.setString(13, position.positionType());
            ps.setBoolean(14, position.isExcluded());
        });
        
        int totalInserted = 0;
        for (int[] batch : results) {
            for (int count : batch) {
                if (count > 0) totalInserted += count;
            }
        }
        
        log.info("Inserted {} positions for batch {}", totalInserted, batchId);
        return totalInserted;
    }
    
    /**
     * Delete positions by account and date.
     */
    @Transactional
    public int deleteByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = "DELETE FROM account_positions WHERE account_id = ? AND business_date = ?";
        int deleted = jdbcTemplate.update(sql, accountId, businessDate);
        log.info("Deleted {} positions for account {} date {}", deleted, accountId, businessDate);
        return deleted;
    }
    
    /**
     * Delete positions by batch ID.
     */
    @Transactional
    public int deleteByBatch(int batchId) {
        String sql = "DELETE FROM account_positions WHERE batch_id = ?";
        return jdbcTemplate.update(sql, batchId);
    }
    
    /**
     * Count positions for account and date.
     */
    public int countByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = "SELECT COUNT(*) FROM account_positions WHERE account_id = ? AND business_date = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, businessDate);
        return count != null ? count : 0;
    }
    
    /**
     * Check if positions exist for account and date.
     */
    public boolean existsByAccountAndDate(int accountId, LocalDate businessDate) {
        return countByAccountAndDate(accountId, businessDate) > 0;
    }
    
    /**
     * Get latest business date for account.
     */
    public LocalDate getLatestBusinessDate(int accountId) {
        String sql = "SELECT MAX(business_date) FROM account_positions WHERE account_id = ?";
        return jdbcTemplate.queryForObject(sql, LocalDate.class, accountId);
    }
    
    /**
     * Get total market value for account and date.
     */
    public BigDecimal getTotalMarketValue(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT COALESCE(SUM(market_value_base), 0) 
            FROM account_positions 
            WHERE account_id = ? AND business_date = ? AND NOT is_excluded
            """;
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId, businessDate);
    }
    
    /**
     * Row mapper for PositionDto.
     */
    private static class PositionRowMapper implements RowMapper<PositionDto> {
        @Override
        public PositionDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PositionDto(
                rs.getLong("position_id"),
                rs.getInt("account_id"),
                rs.getInt("product_id"),
                rs.getObject("business_date", LocalDate.class),
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
        }
    }
}
