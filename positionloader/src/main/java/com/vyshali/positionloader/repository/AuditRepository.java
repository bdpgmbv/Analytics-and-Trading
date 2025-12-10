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

    /**
     * Log an audit event.
     */
    public void log(String eventType, String entityId, String actor, String status) {
        jdbc.update("""
                INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, eventType, entityId, actor, status);
    }

    // ==================== EOD STATUS TRACKING ====================

    /**
     * Mark an account as complete for a business date.
     */
    public void markAccountComplete(Integer accountId, Integer clientId, LocalDate date) {
        jdbc.update("""
                INSERT INTO Eod_Daily_Status (account_id, client_id, business_date, status, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', CURRENT_TIMESTAMP)
                ON CONFLICT (account_id, business_date) DO UPDATE 
                SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP
                """, accountId, clientId, date);
    }

    /**
     * Check if all accounts for a client are complete for a business date.
     */
    public boolean isClientComplete(Integer clientId, LocalDate date) {
        int total = countClientAccounts(clientId);
        if (total == 0) return false;

        Integer done = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Eod_Daily_Status
                WHERE client_id = ? AND business_date = ? AND status = 'COMPLETED'
                """, Integer.class, clientId, date);

        return done != null && done >= total;
    }

    /**
     * Count total accounts for a client.
     * Used for sign-off calculation.
     */
    public int countClientAccounts(Integer clientId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Accounts
                WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)
                """, Integer.class, clientId);
        return count != null ? count : 0;
    }

    // ==================== OPS REQUESTS (Maker-Checker) ====================

    /**
     * Create a pending ops request.
     */
    public void createRequest(String requestId, String actionType, String payload, String maker) {
        jdbc.update("""
                INSERT INTO Ops_Requests (request_id, action_type, payload, requested_by, status, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """, requestId, actionType, payload, maker);
    }

    /**
     * Get an ops request by ID.
     */
    public java.util.Map<String, Object> getRequest(String requestId) {
        try {
            return jdbc.queryForMap("SELECT * FROM Ops_Requests WHERE request_id = ?", requestId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Approve an ops request.
     */
    public void approveRequest(String requestId, String checker) {
        jdbc.update("""
                UPDATE Ops_Requests
                SET status = 'APPROVED', approved_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ?
                """, checker, requestId);
    }

    /**
     * Reject an ops request.
     */
    public void rejectRequest(String requestId, String checker, String reason) {
        jdbc.update("""
                UPDATE Ops_Requests
                SET status = 'REJECTED', approved_by = ?, payload = CONCAT(payload, ' REJECTED: ', ?), updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ?
                """, checker, reason, requestId);
    }
}