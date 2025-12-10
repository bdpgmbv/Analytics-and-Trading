package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - FIXED: SQL statements match actual Positions table schema
 * @author Vyshali Prabananth Lal
 *
 * Schema reference (from 002-trading-tables.sql + 006-add-missing-columns.sql):
 *
 * CREATE TABLE Positions (
 *     position_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *     account_id INT NOT NULL,
 *     product_id INT NOT NULL,
 *     batch_id INT NOT NULL DEFAULT 1,
 *     quantity DECIMAL(18, 6) NOT NULL DEFAULT 0,
 *     avg_cost_price DECIMAL(18, 6) DEFAULT 0,
 *     cost_local DECIMAL(18, 6) DEFAULT 0,
 *     source_system VARCHAR(20) DEFAULT 'MSPM',
 *     position_type VARCHAR(20) DEFAULT 'PHYSICAL',
 *     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     -- Added by 006-add-missing-columns.sql:
 *     system_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     system_to TIMESTAMP DEFAULT '9999-12-31 23:59:59',
 *     market_value_base DECIMAL(18, 6) DEFAULT 0
 * );
 */
public final class PositionSql {

    private PositionSql() {
    } // Prevent instantiation

    // ============================================================
    // BITEMPORAL OPERATIONS
    // ============================================================

    /**
     * Close the old version: Set system_to = NOW() for the currently active row
     * Parameters: [timestamp, accountId, productId]
     */
    public static final String CLOSE_VERSION = """
            UPDATE Positions 
            SET system_to = ?, updated_at = CURRENT_TIMESTAMP
            WHERE account_id = ? 
              AND product_id = ? 
              AND system_to = '9999-12-31 23:59:59'
            """;

    /**
     * Insert new version of a position
     * Parameters: [accountId, productId, quantity, avgCostPrice, costLocal,
     * sourceSystem, batchId, systemFrom]
     */
    public static final String INSERT_VERSION = """
            INSERT INTO Positions (
                account_id, product_id, quantity, 
                avg_cost_price, cost_local, source_system, 
                position_type, batch_id, 
                system_from, system_to, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, 'PHYSICAL', ?, ?, '9999-12-31 23:59:59', CURRENT_TIMESTAMP)
            """;

    /**
     * Get current quantity for a position (active row only)
     * Parameters: [accountId, productId]
     */
    public static final String GET_CURRENT_QUANTITY = """
            SELECT quantity 
            FROM Positions 
            WHERE account_id = ? 
              AND product_id = ? 
              AND system_to = '9999-12-31 23:59:59'
            """;

    /**
     * Get current position with all fields
     * Parameters: [accountId, productId]
     */
    public static final String GET_CURRENT_POSITION = """
            SELECT position_id, account_id, product_id, batch_id,
                   quantity, avg_cost_price, cost_local, 
                   source_system, position_type, market_value_base,
                   system_from, system_to, updated_at
            FROM Positions 
            WHERE account_id = ? 
              AND product_id = ? 
              AND system_to = '9999-12-31 23:59:59'
            """;

    // ============================================================
    // BATCH OPERATIONS
    // ============================================================

    /**
     * Get max batch ID for an account
     * Parameters: [accountId]
     */
    public static final String GET_MAX_BATCH = """
            SELECT COALESCE(MAX(batch_id), 0) 
            FROM Account_Batches 
            WHERE account_id = ?
            """;

    /**
     * Create next batch for an account
     * Parameters: [accountId, batchId]
     */
    public static final String CREATE_NEXT_BATCH = """
            INSERT INTO Account_Batches (account_id, batch_id, status, created_at) 
            VALUES (?, ?, 'STAGING', CURRENT_TIMESTAMP)
            """;

    /**
     * Archive old batches when activating new one
     * Parameters: [accountId]
     */
    public static final String ARCHIVE_OLD_BATCHES = """
            UPDATE Account_Batches 
            SET status = 'ARCHIVED' 
            WHERE account_id = ? 
              AND status = 'ACTIVE'
            """;

    /**
     * Activate a specific batch
     * Parameters: [accountId, batchId]
     */
    public static final String ACTIVATE_BATCH = """
            UPDATE Account_Batches 
            SET status = 'ACTIVE' 
            WHERE account_id = ? 
              AND batch_id = ?
            """;

    /**
     * Delete archived batches (cleanup)
     * Parameters: [accountId]
     */
    public static final String CLEANUP_BATCHES = """
            DELETE FROM Account_Batches 
            WHERE account_id = ? 
              AND status = 'ARCHIVED'
            """;

    // ============================================================
    // SIMPLE UPSERT (Non-bitemporal fallback)
    // ============================================================

    /**
     * Simple upsert without bitemporal - use if migration not run
     * Parameters: [accountId, productId, batchId, quantity, avgCostPrice,
     * costLocal, sourceSystem]
     */
    public static final String SIMPLE_UPSERT = """
            INSERT INTO Positions (
                account_id, product_id, batch_id, quantity, 
                avg_cost_price, cost_local, source_system, position_type, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PHYSICAL', CURRENT_TIMESTAMP)
            ON CONFLICT (account_id, product_id, batch_id) 
            DO UPDATE SET 
                quantity = EXCLUDED.quantity,
                avg_cost_price = EXCLUDED.avg_cost_price,
                cost_local = EXCLUDED.cost_local,
                source_system = EXCLUDED.source_system,
                updated_at = CURRENT_TIMESTAMP
            """;

    /**
     * Delete positions by account
     * Parameters: [accountId]
     */
    public static final String DELETE_BY_ACCOUNT = """
            DELETE FROM Positions 
            WHERE account_id = ?
            """;

    /**
     * Delete positions by account and batch
     * Parameters: [accountId, batchId]
     */
    public static final String DELETE_BY_ACCOUNT_BATCH = """
            DELETE FROM Positions 
            WHERE account_id = ? 
              AND batch_id = ?
            """;

    // ============================================================
    // QUERY OPERATIONS
    // ============================================================

    /**
     * Get all active positions for an account
     * Parameters: [accountId]
     */
    public static final String GET_ACTIVE_POSITIONS = """
            SELECT p.position_id, p.product_id, pr.ticker, pr.asset_class,
                   p.quantity, p.avg_cost_price, p.cost_local, p.market_value_base,
                   p.source_system, p.position_type, p.updated_at
            FROM Positions p
            JOIN Products pr ON p.product_id = pr.product_id
            WHERE p.account_id = ?
              AND p.system_to = '9999-12-31 23:59:59'
            ORDER BY pr.ticker
            """;

    /**
     * Count positions by account
     * Parameters: [accountId]
     */
    public static final String COUNT_BY_ACCOUNT = """
            SELECT COUNT(*) 
            FROM Positions 
            WHERE account_id = ? 
              AND system_to = '9999-12-31 23:59:59'
            """;
}