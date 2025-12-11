package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit logging.
 */
@Repository
public class AuditRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Log an event asynchronously.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String eventType, String entityId, String actor, String payload) {
        String sql = """
            INSERT INTO audit_logs (event_type, entity_id, actor, payload, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql, eventType, entityId, actor, payload);
    }
    
    /**
     * Log an event synchronously.
     */
    @Transactional
    public void logEventSync(String eventType, String entityId, String actor, String payload) {
        String sql = """
            INSERT INTO audit_logs (event_type, entity_id, actor, payload, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql, eventType, entityId, actor, payload);
    }
    
    /**
     * Find recent events by type.
     */
    public List<AuditEvent> findRecentByType(String eventType, int limit) {
        String sql = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM audit_logs
            WHERE event_type = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuditEvent(
            rs.getLong("audit_id"),
            rs.getString("event_type"),
            rs.getString("entity_id"),
            rs.getString("actor"),
            rs.getString("payload"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ), eventType, limit);
    }
    
    /**
     * Find events by entity.
     */
    public List<AuditEvent> findByEntity(String entityId, int limit) {
        String sql = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM audit_logs
            WHERE entity_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuditEvent(
            rs.getLong("audit_id"),
            rs.getString("event_type"),
            rs.getString("entity_id"),
            rs.getString("actor"),
            rs.getString("payload"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ), entityId, limit);
    }
    
    /**
     * Purge old audit logs.
     */
    @Transactional
    public int purgeOld(int daysOld) {
        String sql = """
            DELETE FROM audit_logs 
            WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * ?
            """;
        return jdbcTemplate.update(sql, daysOld);
    }
    
    public record AuditEvent(
        long auditId,
        String eventType,
        String entityId,
        String actor,
        String payload,
        LocalDateTime createdAt
    ) {}
}
