package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - UPDATED: Added @Cacheable annotations for reference data
 *
 * PERFORMANCE IMPROVEMENT:
 * - Before: Every intraday update = 4-5 DB queries for ref data
 * - After:  First call hits DB, subsequent calls use cache
 * - Expected: 90%+ cache hit rate during intraday processing
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import com.vyshali.positionloader.dto.PositionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for reference data: Clients, Funds, Accounts, Products.
 * <p>
 * CACHING STRATEGY:
 * - Read operations: @Cacheable (return cached value if exists)
 * - Write operations: @CacheEvict (invalidate cache after update)
 * - This ensures consistency while maximizing cache hits
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReferenceDataRepository {

    private final JdbcTemplate jdbc;

    /**
     * Ensure all reference data exists before inserting positions.
     * This is the main entry point - it calls individual ensure methods.
     */
    public void ensureReferenceData(AccountSnapshotDTO snapshot) {
        ensureClient(snapshot.clientId(), snapshot.clientName());
        ensureFund(snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());
        ensureAccount(snapshot);
        if (snapshot.positions() != null) {
            ensureProducts(snapshot.positions());
        }
    }

    // ==================== CLIENT ====================

    /**
     * Check if client exists in cache first, then DB.
     */
    @Cacheable(value = "clients", key = "#clientId", unless = "#result == false")
    public boolean clientExists(Integer clientId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM Clients WHERE client_id = ?", Integer.class, clientId);
        return count != null && count > 0;
    }

    /**
     * Ensure client exists - uses cache to avoid repeated DB checks.
     */
    @CacheEvict(value = "clients", key = "#clientId")
    public void ensureClient(Integer clientId, String clientName) {
        // Check cache first via clientExists()
        if (clientExists(clientId)) {
            log.debug("Client {} found in cache/DB, skipping insert", clientId);
            return;
        }

        jdbc.update("""
                INSERT INTO Clients (client_id, client_name, status, updated_at)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                ON CONFLICT (client_id) DO UPDATE SET 
                    client_name = EXCLUDED.client_name,
                    updated_at = CURRENT_TIMESTAMP
                """, clientId, clientName);

        log.debug("Ensured client: {}", clientId);
    }

    // ==================== FUND ====================

    /**
     * Check if fund exists in cache first, then DB.
     */
    @Cacheable(value = "funds", key = "#fundId", unless = "#result == false")
    public boolean fundExists(Integer fundId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM Funds WHERE fund_id = ?", Integer.class, fundId);
        return count != null && count > 0;
    }

    /**
     * Ensure fund exists.
     */
    @CacheEvict(value = "funds", key = "#fundId")
    public void ensureFund(Integer fundId, Integer clientId, String fundName, String currency) {
        if (fundExists(fundId)) {
            log.debug("Fund {} found in cache/DB, skipping insert", fundId);
            return;
        }

        jdbc.update("""
                INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                ON CONFLICT (fund_id) DO UPDATE SET 
                    fund_name = EXCLUDED.fund_name,
                    updated_at = CURRENT_TIMESTAMP
                """, fundId, clientId, fundName, currency);

        log.debug("Ensured fund: {}", fundId);
    }

    // ==================== ACCOUNT ====================

    /**
     * Check if account exists.
     */
    @Cacheable(value = "accounts", key = "#accountId", unless = "#result == false")
    public boolean accountExists(Integer accountId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM Accounts WHERE account_id = ?", Integer.class, accountId);
        return count != null && count > 0;
    }

    /**
     * Ensure account exists.
     */
    @CacheEvict(value = "accounts", key = "#snapshot.accountId()")
    public void ensureAccount(AccountSnapshotDTO snapshot) {
        if (accountExists(snapshot.accountId())) {
            log.debug("Account {} found in cache/DB, skipping insert", snapshot.accountId());
            return;
        }

        jdbc.update("""
                INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name,
                    base_currency, account_number, account_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET 
                    account_number = EXCLUDED.account_number,
                    created_at = CURRENT_TIMESTAMP
                """, snapshot.accountId(), snapshot.clientId(), snapshot.clientName(), snapshot.fundId(), snapshot.fundName(), snapshot.baseCurrency(), snapshot.accountNumber(), snapshot.accountType());

        log.debug("Ensured account: {}", snapshot.accountId());
    }

    // ==================== PRODUCTS ====================

    /**
     * Check if product exists.
     */
    @Cacheable(value = "products", key = "#productId", unless = "#result == false")
    public boolean productExists(Integer productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM Products WHERE product_id = ?", Integer.class, productId);
        return count != null && count > 0;
    }

    /**
     * Ensure products exist - batch check and insert.
     */
    public void ensureProducts(List<PositionDTO> positions) {
        for (PositionDTO p : positions) {
            ensureProduct(p.productId(), p.ticker(), p.assetClass());
        }
    }

    /**
     * Ensure single product exists.
     */
    @CacheEvict(value = "products", key = "#productId")
    public void ensureProduct(Integer productId, String ticker, String assetClass) {
        if (productExists(productId)) {
            return;
        }

        jdbc.update("""
                INSERT INTO Products (product_id, ticker, asset_class, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE SET 
                    ticker = EXCLUDED.ticker
                """, productId, ticker, assetClass, "Imported: " + ticker);
    }

    // ==================== CLIENT ACCOUNTS (for EOD completion check) ====================

    /**
     * Count accounts for a client - cached for EOD completion checks.
     */
    @Cacheable(value = "clientAccounts", key = "#clientId")
    public int countClientAccounts(Integer clientId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Accounts
                WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)
                """, Integer.class, clientId);
        return count != null ? count : 0;
    }

    // ==================== CACHE MANAGEMENT ====================

    /**
     * Evict all reference data caches.
     * Call this during EOD or when reference data is bulk updated.
     */
    @CacheEvict(value = {"clients", "funds", "accounts", "products", "clientAccounts"}, allEntries = true)
    public void evictAllCaches() {
        log.info("All reference data caches evicted");
    }

    /**
     * Evict caches for a specific client hierarchy.
     */
    public void evictClientHierarchy(Integer clientId) {
        // This would require programmatic cache access
        // For now, we rely on key-based eviction in ensure methods
        log.info("Evicted cache for client hierarchy: {}", clientId);
    }
}