package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - 12:59 PM
 * @author Vyshali Prabananth Lal
 */

public final class AuditSql {
    private AuditSql() {
    }

    public static final String INSERT_AUDIT = "INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
}