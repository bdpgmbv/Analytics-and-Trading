package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - 12:59 PM
 * FIXED: Changed table name from Audit_Logs to match 005-missing-tables.sql
 * @author Vyshali Prabananth Lal
 */

public final class AuditSql {
    private AuditSql() {
    }

    /**
     * Insert audit log entry
     * Table: Audit_Logs (defined in 005-missing-tables.sql)
     * <p>
     * Columns: audit_id (auto), event_type, entity_id, actor, payload, created_at (auto)
     */
    public static final String INSERT_AUDIT = """
            INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at) 
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

    /**
     * Query audit logs by entity
     */
    public static final String FIND_BY_ENTITY = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM Audit_Logs
            WHERE entity_id = ?
            ORDER BY created_at DESC
            """;

    /**
     * Query audit logs by actor
     */
    public static final String FIND_BY_ACTOR = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM Audit_Logs
            WHERE actor = ?
            ORDER BY created_at DESC
            """;

    /**
     * Query audit logs by event type and date range
     */
    public static final String FIND_BY_TYPE_AND_DATE = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM Audit_Logs
            WHERE event_type = ?
              AND created_at BETWEEN ? AND ?
            ORDER BY created_at DESC
            """;
}