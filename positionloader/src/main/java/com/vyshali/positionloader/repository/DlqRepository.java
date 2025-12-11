package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Dead Letter Queue operations.
 */
@Repository
public class DlqRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public DlqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Add message to DLQ.
     */
    @Transactional
    public long insert(String topic, String messageKey, String payload, String errorMessage) {
        String sql = """
            INSERT INTO dlq (topic, message_key, payload, error_message, status, created_at)
            VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
            RETURNING id
            """;
        return jdbcTemplate.queryForObject(sql, Long.class, topic, messageKey, payload, errorMessage);
    }
    
    /**
     * Get messages ready for retry.
     */
    public List<DlqMessage> findRetryable(int limit) {
        String sql = """
            SELECT id, topic, message_key, payload, retry_count 
            FROM dlq 
            WHERE status = 'PENDING' 
            AND retry_count < max_retries
            AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
            ORDER BY created_at
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DlqMessage(
            rs.getLong("id"),
            rs.getString("topic"),
            rs.getString("message_key"),
            rs.getString("payload"),
            rs.getInt("retry_count")
        ), limit);
    }
    
    /**
     * Increment retry count and set next retry time.
     */
    @Transactional
    public void incrementRetry(long id, LocalDateTime nextRetryAt) {
        String sql = """
            UPDATE dlq 
            SET retry_count = retry_count + 1, 
                last_retry_at = CURRENT_TIMESTAMP,
                next_retry_at = ?
            WHERE id = ?
            """;
        jdbcTemplate.update(sql, nextRetryAt, id);
    }
    
    /**
     * Mark message as processed.
     */
    @Transactional
    public void markProcessed(long id) {
        String sql = "UPDATE dlq SET status = 'PROCESSED' WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
    
    /**
     * Mark message as failed (exceeded retries).
     */
    @Transactional
    public void markFailed(long id) {
        String sql = "UPDATE dlq SET status = 'FAILED' WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
    
    /**
     * Count pending messages.
     */
    public int countPending() {
        String sql = "SELECT COUNT(*) FROM dlq WHERE status = 'PENDING'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Count pending messages by topic.
     */
    public int countPendingByTopic(String topic) {
        String sql = "SELECT COUNT(*) FROM dlq WHERE status = 'PENDING' AND topic = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, topic);
        return count != null ? count : 0;
    }
    
    /**
     * Delete old processed messages.
     */
    @Transactional
    public int purgeOld(int daysOld) {
        String sql = """
            DELETE FROM dlq 
            WHERE status IN ('PROCESSED', 'FAILED') 
            AND created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * ?
            """;
        return jdbcTemplate.update(sql, daysOld);
    }
    
    public record DlqMessage(long id, String topic, String messageKey, String payload, int retryCount) {}
}
