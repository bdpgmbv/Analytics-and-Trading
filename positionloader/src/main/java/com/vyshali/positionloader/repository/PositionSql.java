package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - Refactored for Bitemporal Architecture
 * @author Vyshali Prabananth Lal
 */

public final class PositionSql {

    private PositionSql() {
    } // Prevent instantiation

    // 1. Close the old version: Set system_to = NOW() for the currently active row
    public static final String CLOSE_VERSION = """
                UPDATE Positions 
                SET system_to = ? 
                WHERE account_id = ? 
                  AND product_id = ? 
                  AND system_to = '9999-12-31 00:00:00'
            """;

    // 2. Insert the new version: Valid from NOW() until INFINITY
    public static final String INSERT_VERSION = """
                INSERT INTO Positions (
                    account_id, product_id, quantity, 
                    avg_cost_price, cost_local, source_system, 
                    position_type, batch_id, 
                    system_from, system_to
                )
                VALUES (?, ?, ?, ?, ?, ?, 'PHYSICAL', ?, ?, '9999-12-31 00:00:00')
            """;

    // 3. For Intraday Updates: Helper to get the current quantity if it exists
    public static final String GET_CURRENT_QUANTITY = """
                SELECT quantity 
                FROM Positions 
                WHERE account_id = ? 
                  AND product_id = ? 
                  AND system_to = '9999-12-31 00:00:00'
            """;

    // (Optional) Legacy Batch Support - kept to prevent compilation errors if other classes use it
    public static final String GET_MAX_BATCH = "SELECT COALESCE(MAX(batch_id), 0) FROM Account_Batches WHERE account_id = ?";
    public static final String CREATE_NEXT_BATCH = "INSERT INTO Account_Batches (account_id, batch_id, status) VALUES (?, ?, 'ACTIVE')";
    public static final String ARCHIVE_OLD_BATCHES = "UPDATE Account_Batches SET status = 'ARCHIVED' WHERE account_id = ? AND status = 'ACTIVE'";
    public static final String CLEANUP_BATCHES = "DELETE FROM Account_Batches WHERE account_id = ? AND status = 'ARCHIVED'";
}