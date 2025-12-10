package com.vyshali.positionloader.repository;

/*
 * 12/10/2025 - FIXED: Added missing maker-checker methods
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Map;

/**
 * Repository for audit logs, EOD status tracking, and maker-checker requests.
 */
@Repository
@RequiredArgsConstructor
public class AuditRepository {

    private final JdbcTemplate jdbc;

    // ==================== AUDIT LOGS ====================

    /**
     * Log an audit event.
     */
    public void log(String eventType, String entityId, String actor, String payload) {
        jdbc.update("""
                INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, eventType, entityId, actor, payload);
    }

    // ==================== EOD STATUS ====================

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
     * Check if all accounts for a client are complete.
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
     * Count accounts for a client.
     */
    public int countClientAccounts(Integer clientId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Accounts
                WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)
                """, Integer.class, clientId);
        return count != null ? count : 0;
    }

    // ==================== MAKER-CHECKER REQUESTS ====================

    /**
     * Create a pending approval request.
     */
    public void createRequest(String requestId, String actionType, String payload, String requestedBy) {
        jdbc.update("""
                INSERT INTO Ops_Requests (request_id, action_type, payload, status, requested_by, created_at)
                VALUES (?, ?, ?, 'PENDING', ?, CURRENT_TIMESTAMP)
                """, requestId, actionType, payload, requestedBy);
    }

    /**
     * Get a request by ID.
     */
    public Map<String, Object> getRequest(String requestId) {
        try {
            return jdbc.queryForMap("""
                    SELECT request_id, action_type, payload, status, requested_by, approved_by, created_at
                    FROM Ops_Requests
                    WHERE request_id = ?
                    """, requestId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Approve a request (update status and set approver).
     */
    public void approveRequest(String requestId, String approvedBy) {
        jdbc.update("""
                UPDATE Ops_Requests
                SET status = 'APPROVED', approved_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ?
                """, approvedBy, requestId);
    }

    /**
     * Reject a request.
     */
    public void rejectRequest(String requestId, String rejectedBy) {
        jdbc.update("""
                UPDATE Ops_Requests
                SET status = 'REJECTED', approved_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE request_id = ?
                """, rejectedBy, requestId);
    }
}