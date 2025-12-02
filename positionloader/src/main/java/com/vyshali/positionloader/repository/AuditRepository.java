package com.vyshali.positionloader.repository;

/*
 * 12/02/2025 - 1:37 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Writes an audit entry to the Ops_Audit_Log table.
     * * @param actionType e.g., "TRIGGER_EOD"
     *
     * @param targetId e.g., Account ID "1001"
     * @param user     e.g., "admin-user" (from OAuth token)
     * @param status   e.g., "STARTED", "SUCCESS", "FAILED"
     */
    public void logAction(String actionType, String targetId, String user, String status) {
        String sql = """
                    INSERT INTO Ops_Audit_Log (action_type, target_id, triggered_by, status, triggered_at) 
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;

        jdbcTemplate.update(sql, actionType, targetId, user, status);
    }
}
