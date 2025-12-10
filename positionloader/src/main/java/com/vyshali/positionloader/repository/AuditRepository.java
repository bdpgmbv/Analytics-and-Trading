package com.vyshali.positionloader.repository;

/*
 * 12/02/2025 - 1:37 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * Repository for audit logs and EOD status tracking.
 */
@Repository
@RequiredArgsConstructor
public class AuditRepository {

    private final JdbcTemplate jdbc;

    // ==================== AUDIT LOGS ====================

    public void log(String eventType, String entityId, String actor, String status) {
        jdbc.update("""
                INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at) 
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, eventType, entityId, actor, status);
    }

    // ==================== EOD STATUS TRACKING ====================

    public void markAccountComplete(Integer accountId, Integer clientId, LocalDate date) {
        jdbc.update("""
                INSERT INTO Eod_Daily_Status (account_id, client_id, business_date, status) 
                VALUES (?, ?, ?, 'COMPLETED') 
                ON CONFLICT (account_id, business_date) DO NOTHING
                """, accountId, clientId, date);
    }

    public boolean isClientComplete(Integer clientId, LocalDate date) {
        // Count total accounts for client
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Accounts 
                WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)
                """, Integer.class, clientId);

        // Count completed accounts
        Integer done = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Eod_Daily_Status 
                WHERE client_id = ? AND business_date = ?
                """, Integer.class, clientId, date);

        return total != null && total > 0 && total.equals(done);
    }

    // ==================== OPS REQUESTS (Maker-Checker) ====================

    public void createRequest(String requestId, String actionType, String payload, String maker) {
        jdbc.update("""
                INSERT INTO Ops_Requests (request_id, action_type, payload, requested_by, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, requestId, actionType, payload, maker);
    }

    public java.util.Map<String, Object> getRequest(String requestId) {
        try {
            return jdbc.queryForMap("SELECT * FROM Ops_Requests WHERE request_id = ?", requestId);
        } catch (Exception e) {
            return null;
        }
    }

    public void approveRequest(String requestId, String checker) {
        jdbc.update("""
                UPDATE Ops_Requests 
                SET status = 'APPROVED', approved_by = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE request_id = ?
                """, checker, requestId);
    }
}