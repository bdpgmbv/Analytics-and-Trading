package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Refactored for Bitemporal Architecture
 * FIXED: Column names now match schema after 006-add-missing-columns.sql migration
 * @author Vyshali Prabananth Lal
 */

public final class PositionSql {

    private PositionSql() {
    } // Prevent instantiation

    // ============================================================
    // BITEMPORAL OPERATIONS
    // Requires: 006-add-missing-columns.sql to add system_from, system_to
    // ============================================================

    /**
     * Close the old version: Set system_to = NOW() for the currently active row
     */
    public static final String CLOSE_VERSION = """
            UPDATE Positions 
            SET system_to = ? 
            WHERE account_id = ? 
              AND product_id = ? 
              AND system_to = '9999-12-31 00:00:00'
            """;

    /**
     * Insert the new version: Valid from NOW() until INFINITY
     */
    public static final String INSERT_VERSION = """
            INSERT INTO Positions (
                account_id, product_id, quantity, 
                avg_cost_price, cost_local, source_system, 
                position_type, batch_id, 
                system_from, system_to
            )
            VALUES (?, ?, ?, ?, ?, ?, 'PHYSICAL', ?, ?, '9999-12-31 00:00:00')
            """;

    /**
     * Get the current quantity for a position (active row only)
     */
    public static final String GET_CURRENT_QUANTITY = """
            SELECT quantity 
            FROM Positions 
            WHERE account_id = ? 
              AND product_id = ? 
              AND system_to = '9999-12-31 00:00:00'
            """;

    // ============================================================
    // BATCH OPERATIONS (Legacy support)
    // ============================================================

    /**
     * Get max batch ID for an account
     */
    public static final String GET_MAX_BATCH = """
            SELECT COALESCE(MAX(batch_id), 0) 
            FROM Account_Batches 
            WHERE account_id = ?
            """;

    /**
     * Create next batch for an account
     */
    public static final String CREATE_NEXT_BATCH = """
            INSERT INTO Account_Batches (account_id, batch_id, status) 
            VALUES (?, ?, 'STAGING')
            """;

    /**
     * Archive old batches when activating new one
     */
    public static final String ARCHIVE_OLD_BATCHES = """
            UPDATE Account_Batches 
            SET status = 'ARCHIVED' 
            WHERE account_id = ? 
              AND status = 'ACTIVE'
            """;

    /**
     * Activate a specific batch
     */
    public static final String ACTIVATE_BATCH = """
            UPDATE Account_Batches 
            SET status = 'ACTIVE' 
            WHERE account_id = ? 
              AND batch_id = ?
            """;

    /**
     * Delete archived batches (cleanup)
     */
    public static final String CLEANUP_BATCHES = """
            DELETE FROM Account_Batches 
            WHERE account_id = ? 
              AND status = 'ARCHIVED'
            """;

    // ============================================================
    // NON-BITEMPORAL FALLBACK
    // Use these if you haven't run the migration yet
    // ============================================================

    /**
     * Simple upsert without bitemporal columns
     * Use this if system_from/system_to columns don't exist yet
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
     * Delete positions by account (for cleanup)
     */
    public static final String DELETE_BY_ACCOUNT = """
            DELETE FROM Positions 
            WHERE account_id = ?
            """;

    /**
     * Delete positions by account and batch (for batch cleanup)
     */
    public static final String DELETE_BY_ACCOUNT_BATCH = """
            DELETE FROM Positions 
            WHERE account_id = ? 
              AND batch_id = ?
            """;
}