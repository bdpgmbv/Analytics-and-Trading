package com.vyshali.common.repository;

import com.vyshali.common.dto.SharedDto.AuditEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for audit logging.
 * Provides async logging to avoid impacting main transaction performance.
 */
@Repository
public class AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Log an event asynchronously (non-blocking).
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String eventType, String entityId, String actor, String payload) {
        try {
            insertLog(eventType, entityId, actor, payload);
        } catch (Exception e) {
            log.warn("Failed to log audit event: {}", e.getMessage());
        }
    }

    /**
     * Log an event synchronously.
     */
    @Transactional
    public void log(String eventType, String entityId, String actor, String payload) {
        insertLog(eventType, entityId, actor, payload);
    }

    /**
     * Log with simple parameters.
     */
    public void log(String eventType, int entityId, String details) {
        logAsync(eventType, String.valueOf(entityId), "SYSTEM", details);
    }

    /**
     * Log with business date context.
     */
    public void log(String eventType, int entityId, LocalDate businessDate, String details) {
        String payload = String.format("{\"businessDate\":\"%s\",\"details\":\"%s\"}", 
                businessDate, escapeJson(details));
        logAsync(eventType, String.valueOf(entityId), "SYSTEM", payload);
    }

    /**
     * Log with actor.
     */
    public void log(String eventType, int entityId, String actor, String details) {
        logAsync(eventType, String.valueOf(entityId), actor, details);
    }

    /**
     * Log with full context.
     */
    public void log(String eventType, int entityId, LocalDate businessDate, String actor, String details) {
        String payload = String.format("{\"businessDate\":\"%s\",\"details\":\"%s\"}", 
                businessDate, escapeJson(details));
        logAsync(eventType, String.valueOf(entityId), actor, payload);
    }

    private void insertLog(String eventType, String entityId, String actor, String payload) {
        String sql = """
            INSERT INTO audit_logs (event_type, entity_id, actor, payload, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        jdbcTemplate.update(sql, eventType, entityId, actor, payload);
    }

    /**
     * Find recent events by type.
     */
    public List<AuditEventDTO> findRecentByType(String eventType, int limit) {
        String sql = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM audit_logs
            WHERE event_type = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuditEventDTO(
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
    public List<AuditEventDTO> findByEntity(String entityId, int limit) {
        String sql = """
            SELECT audit_id, event_type, entity_id, actor, payload, created_at
            FROM audit_logs
            WHERE entity_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuditEventDTO(
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
    public int purgeOlderThan(int days) {
        String sql = """
            DELETE FROM audit_logs 
            WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * ?
            """;
        return jdbcTemplate.update(sql, days);
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
