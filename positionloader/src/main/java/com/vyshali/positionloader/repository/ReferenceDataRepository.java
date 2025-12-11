package com.vyshali.positionloader.repository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Repository for reference data (accounts, products, FX rates).
 */
@Repository
public class ReferenceDataRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public ReferenceDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Check if account exists.
     */
    @Cacheable(value = "accounts", key = "'exists-' + #accountId")
    public boolean accountExists(int accountId) {
        String sql = "SELECT COUNT(*) FROM accounts WHERE account_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId);
        return count != null && count > 0;
    }
    
    /**
     * Check if product exists.
     */
    @Cacheable(value = "products", key = "'exists-' + #productId")
    public boolean productExists(int productId) {
        String sql = "SELECT COUNT(*) FROM products WHERE product_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId);
        return count != null && count > 0;
    }
    
    /**
     * Find product ID by ticker.
     */
    @Cacheable(value = "products", key = "'ticker-' + #ticker")
    public Integer findProductIdByTicker(String ticker) {
        String sql = "SELECT product_id FROM products WHERE ticker = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("product_id");
            }
            return null;
        }, ticker);
    }
    
    /**
     * Find account ID by order ID (from client_orders table).
     */
    public Integer findAccountIdByOrderId(String orderId) {
        String sql = "SELECT account_id FROM client_orders WHERE order_id = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getInt("account_id");
            }
            return null;
        }, orderId);
    }
    
    /**
     * Get base currency for account.
     */
    @Cacheable(value = "accounts", key = "'currency-' + #accountId")
    public String getAccountBaseCurrency(int accountId) {
        String sql = "SELECT base_currency FROM accounts WHERE account_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, accountId);
    }
    
    /**
     * Get client ID for account.
     */
    @Cacheable(value = "accounts", key = "'client-' + #accountId")
    public Integer getClientId(int accountId) {
        String sql = "SELECT client_id FROM accounts WHERE account_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, accountId);
    }
    
    /**
     * Get FX rate.
     */
    @Cacheable(value = "fxRates", key = "#currencyPair + '-' + #rateDate")
    public BigDecimal getFxRate(String currencyPair, LocalDate rateDate) {
        String sql = """
            SELECT rate FROM fx_rates 
            WHERE currency_pair = ? AND rate_date <= ?
            ORDER BY rate_date DESC LIMIT 1
            """;
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getBigDecimal("rate");
            }
            return BigDecimal.ONE; // Default to 1 if not found
        }, currencyPair, rateDate);
    }
    
    /**
     * Get all account IDs for a client.
     */
    public java.util.List<Integer> getAccountIdsForClient(int clientId) {
        String sql = "SELECT account_id FROM accounts WHERE client_id = ? ORDER BY account_id";
        return jdbcTemplate.queryForList(sql, Integer.class, clientId);
    }
    
    /**
     * Get product ticker.
     */
    @Cacheable(value = "products", key = "'id-' + #productId")
    public String getProductTicker(int productId) {
        String sql = "SELECT ticker FROM products WHERE product_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, productId);
    }
}
