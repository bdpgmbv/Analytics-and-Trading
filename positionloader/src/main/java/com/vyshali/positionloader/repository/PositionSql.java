package com.vyshali.positionloader.repository;

/*
 * 12/08/2025 - 5:38 PM
 * @author Vyshali Prabananth Lal
 */

public final class PositionSql {

    private PositionSql() {
    } // Prevent instantiation

    public static final String CREATE_NEXT_BATCH = """
                INSERT INTO Account_Batches (account_id, batch_id, status) 
                VALUES (?, ?, 'STAGING')
            """;

    public static final String GET_MAX_BATCH = """
                SELECT COALESCE(MAX(batch_id), 0) 
                FROM Account_Batches 
                WHERE account_id = ?
            """;

    public static final String ACTIVATE_BATCH = """
                UPDATE Account_Batches 
                SET status = 'ACTIVE' 
                WHERE account_id = ? AND batch_id = ?
            """;

    public static final String ARCHIVE_OLD_BATCHES = """
                UPDATE Account_Batches 
                SET status = 'ARCHIVED' 
                WHERE account_id = ? AND status = 'ACTIVE'
            """;

    public static final String CLEANUP_POSITIONS = """
                DELETE FROM Positions 
                WHERE account_id = ? 
                  AND batch_id IN (
                      SELECT batch_id FROM Account_Batches 
                      WHERE account_id = ? AND status = 'ARCHIVED'
                  )
            """;

    public static final String CLEANUP_BATCHES = """
                DELETE FROM Account_Batches 
                WHERE account_id = ? AND status = 'ARCHIVED'
            """;

    public static final String BATCH_INSERT_POSITION = """
                INSERT INTO Positions (
                    position_id, account_id, product_id, 
                    quantity, avg_cost_price, cost_local, 
                    source_system, position_type, batch_id
                )
                VALUES (nextval('position_seq'), ?, ?, ?, ?, ?, ?, 'PHYSICAL', ?)
            """;

    public static final String GET_ACTIVE_BATCH_ID = """
                SELECT batch_id FROM Account_Batches 
                WHERE account_id = ? AND status = 'ACTIVE'
            """;

    public static final String UPSERT_INTRADAY = """
                INSERT INTO Positions (
                    account_id, product_id, quantity, avg_cost_price, cost_local, source_system, position_type, batch_id
                )
                VALUES (
                    ?, ?, 
                    CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END, 
                    ?, 
                    (CASE WHEN ? IN ('SELL', 'SHORT_SELL') THEN -? ELSE ? END) * ?,
                    ?, 'PHYSICAL', ?
                )
                ON CONFLICT (account_id, product_id) WHERE batch_id = ? 
                DO UPDATE SET 
                    avg_cost_price = CASE 
                        WHEN (Positions.quantity + EXCLUDED.quantity) <> 0 THEN 
                            ( (Positions.quantity * Positions.avg_cost_price) + (EXCLUDED.quantity * EXCLUDED.avg_cost_price) ) 
                            / (Positions.quantity + EXCLUDED.quantity)
                        ELSE Positions.avg_cost_price 
                    END,
                    quantity = Positions.quantity + EXCLUDED.quantity, 
                    cost_local = Positions.cost_local + EXCLUDED.cost_local,
                    source_system = EXCLUDED.source_system,
                    updated_at = CURRENT_TIMESTAMP
            """;

    public static final String UPDATE_QTY_LIFECYCLE = """
                UPDATE Positions 
                SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP
                WHERE account_id = ? 
                  AND product_id = ? 
                  AND batch_id = (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE')
            """;

    public static final String GET_CURRENT_QTY = """
                SELECT COALESCE(SUM(quantity), 0)
                FROM Positions 
                WHERE account_id = ? 
                  AND product_id = ? 
                  AND batch_id = (SELECT batch_id FROM Account_Batches WHERE account_id = ? AND status = 'ACTIVE')
            """;
}
