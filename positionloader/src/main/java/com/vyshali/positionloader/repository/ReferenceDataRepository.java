package com.vyshali.positionloader.repository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
     * Check if account is active.
     */
    @Cacheable(value = "accounts", key = "'active-' + #accountId")
    public boolean isAccountActive(int accountId) {
        // First check if status column exists, fall back to just checking existence
        try {
            String sql = "SELECT COUNT(*) FROM accounts WHERE account_id = ? AND (status IS NULL OR status = 'ACTIVE')";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId);
            return count != null && count > 0;
        } catch (Exception e) {
            // Fallback if status column doesn't exist
            return accountExists(accountId);
        }
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
     * Check if product is valid (exists and not inactive).
     */
    @Cacheable(value = "products", key = "'valid-' + #productId")
    public boolean isProductValid(int productId) {
        // First check if status column exists, fall back to just checking existence
        try {
            String sql = "SELECT COUNT(*) FROM products WHERE product_id = ? AND (status IS NULL OR status != 'INACTIVE')";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId);
            return count != null && count > 0;
        } catch (Exception e) {
            // Fallback if status column doesn't exist
            return productExists(productId);
        }
    }
    
    /**
     * Get account name.
     */
    @Cacheable(value = "accounts", key = "'name-' + #accountId")
    public String getAccountName(int accountId) {
        try {
            String sql = "SELECT COALESCE(account_name, account_number, CAST(account_id AS VARCHAR)) FROM accounts WHERE account_id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, accountId);
        } catch (Exception e) {
            return String.valueOf(accountId);
        }
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
        try {
            String sql = "SELECT account_id FROM client_orders WHERE order_id = ?";
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return rs.getInt("account_id");
                }
                return null;
            }, orderId);
        } catch (Exception e) {
            // Table might not exist
            return null;
        }
    }
    
    /**
     * Get base currency for account.
     */
    @Cacheable(value = "accounts", key = "'currency-' + #accountId")
    public String getAccountBaseCurrency(int accountId) {
        try {
            String sql = "SELECT COALESCE(base_currency, 'USD') FROM accounts WHERE account_id = ?";
            String currency = jdbcTemplate.queryForObject(sql, String.class, accountId);
            return currency != null ? currency : "USD";
        } catch (Exception e) {
            return "USD";
        }
    }
    
    /**
     * Get client ID for account.
     */
    @Cacheable(value = "accounts", key = "'client-' + #accountId")
    public Integer getClientId(int accountId) {
        try {
            String sql = "SELECT client_id FROM accounts WHERE account_id = ?";
            return jdbcTemplate.queryForObject(sql, Integer.class, accountId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get FX rate.
     */
    @Cacheable(value = "fxRates", key = "#currencyPair + '-' + #rateDate")
    public BigDecimal getFxRate(String currencyPair, LocalDate rateDate) {
        try {
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
        } catch (Exception e) {
            return BigDecimal.ONE;
        }
    }
    
    /**
     * Get all account IDs for a client.
     */
    public List<Integer> getAccountIdsForClient(int clientId) {
        String sql = "SELECT account_id FROM accounts WHERE client_id = ? ORDER BY account_id";
        return jdbcTemplate.queryForList(sql, Integer.class, clientId);
    }
    
    /**
     * Get product ticker.
     */
    @Cacheable(value = "products", key = "'id-' + #productId")
    public String getProductTicker(int productId) {
        try {
            String sql = "SELECT ticker FROM products WHERE product_id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, productId);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get all active account IDs.
     */
    public List<Integer> getAllActiveAccountIds() {
        try {
            String sql = "SELECT account_id FROM accounts WHERE status IS NULL OR status = 'ACTIVE' ORDER BY account_id";
            return jdbcTemplate.queryForList(sql, Integer.class);
        } catch (Exception e) {
            // Fallback if status column doesn't exist
            String sql = "SELECT account_id FROM accounts ORDER BY account_id";
            return jdbcTemplate.queryForList(sql, Integer.class);
        }
    }
}
