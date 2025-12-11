package com.vyshali.positionloader.repository;

import com.fxanalyzer.positionloader.dto.PositionDto;
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
    
    public PositionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    private static final RowMapper<PositionDto> POSITION_MAPPER = (rs, rowNum) -> 
        new PositionDto(
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
    
    /**
     * Find positions by account and date.
     */
    public List<PositionDto> findByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT * FROM positions 
            WHERE account_id = ? AND business_date = ? 
            AND is_excluded = FALSE
            AND system_to = '9999-12-31 23:59:59'
            ORDER BY product_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, accountId, businessDate);
    }
    
    /**
     * Find active positions (current batch) for account.
     */
    public List<PositionDto> findActiveByAccount(int accountId) {
        String sql = """
            SELECT p.* FROM positions p
            JOIN account_batches ab ON p.account_id = ab.account_id AND p.batch_id = ab.batch_id
            WHERE p.account_id = ? 
            AND ab.status = 'ACTIVE'
            AND p.is_excluded = FALSE
            ORDER BY p.product_id
            """;
        return jdbcTemplate.query(sql, POSITION_MAPPER, accountId);
    }
    
    /**
     * Count positions for account on date.
     */
    public int countByAccountAndDate(int accountId, LocalDate businessDate) {
        String sql = """
            SELECT COUNT(*) FROM positions 
            WHERE account_id = ? AND business_date = ? AND is_excluded = FALSE
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, businessDate);
        return count != null ? count : 0;
    }
    
    /**
     * Insert a batch of positions.
     */
    @Transactional
    public void insertBatch(List<PositionDto> positions, int batchId) {
        String sql = """
            INSERT INTO positions (
                account_id, product_id, business_date, quantity, price, currency,
                market_value_local, market_value_base, avg_cost_price, cost_local,
                batch_id, source, position_type, is_excluded
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.batchUpdate(sql, positions, 1000, (ps, position) -> {
            ps.setInt(1, position.accountId());
            ps.setInt(2, position.productId());
            ps.setDate(3, java.sql.Date.valueOf(position.businessDate()));
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
    }
    
    /**
     * Upsert a single position.
     */
    @Transactional
    public void upsertPosition(PositionDto position) {
        String sql = """
            INSERT INTO positions (
                account_id, product_id, business_date, quantity, price, currency,
                market_value_local, market_value_base, batch_id, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (account_id, product_id, business_date) 
            DO UPDATE SET 
                quantity = EXCLUDED.quantity,
                price = EXCLUDED.price,
                market_value_local = EXCLUDED.market_value_local,
                market_value_base = EXCLUDED.market_value_base,
                updated_at = CURRENT_TIMESTAMP
            """;
        
        jdbcTemplate.update(sql,
            position.accountId(),
            position.productId(),
            position.businessDate(),
            position.quantity(),
            position.price(),
            position.currency(),
            position.marketValueLocal(),
            position.marketValueBase(),
            position.batchId(),
            position.source()
        );
    }
    
    /**
     * Update position quantity (for trade fills).
     */
    @Transactional
    public void updateQuantity(int accountId, int productId, LocalDate businessDate,
            BigDecimal quantityDelta, BigDecimal price) {
        String sql = """
            UPDATE positions SET 
                quantity = quantity + ?,
                price = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE account_id = ? AND product_id = ? AND business_date = ?
            """;
        
        int updated = jdbcTemplate.update(sql, quantityDelta, price, 
            accountId, productId, businessDate);
        
        // If no row exists, insert new position
        if (updated == 0) {
            String insertSql = """
                INSERT INTO positions (account_id, product_id, business_date, quantity, price, source)
                VALUES (?, ?, ?, ?, ?, 'TRADE_FILL')
                """;
            jdbcTemplate.update(insertSql, accountId, productId, businessDate, 
                quantityDelta, price);
        }
    }
    
    /**
     * Delete positions for a batch (for rollback).
     */
    @Transactional
    public int deleteByBatch(int accountId, int batchId) {
        String sql = "DELETE FROM positions WHERE account_id = ? AND batch_id = ?";
        return jdbcTemplate.update(sql, accountId, batchId);
    }
}
