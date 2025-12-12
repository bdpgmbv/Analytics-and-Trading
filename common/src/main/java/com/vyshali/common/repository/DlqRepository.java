package com.vyshali.common.repository;

import com.vyshali.common.dto.SharedDto.DlqMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Dead Letter Queue operations.
 * Provides consistent DLQ handling across all Kafka consumers.
 */
@Repository
public class DlqRepository {

    private static final Logger log = LoggerFactory.getLogger(DlqRepository.class);
    private final JdbcTemplate jdbcTemplate;

    // DLQ statuses
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";

    public DlqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Add message to DLQ.
     */
    @Transactional
    public long insert(String topic, String messageKey, String payload, String errorMessage) {
        try {
            String sql = """
                INSERT INTO dlq (topic, message_key, payload, error_message, status, retry_count, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                RETURNING id
                """;
            Long id = jdbcTemplate.queryForObject(sql, Long.class, topic, messageKey, payload, errorMessage);
            return id != null ? id : -1;
        } catch (Exception e) {
            // Fallback for databases that don't support RETURNING
            String insertSql = """
                INSERT INTO dlq (topic, message_key, payload, error_message, status, retry_count, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                """;
            jdbcTemplate.update(insertSql, topic, messageKey, payload, errorMessage);
            log.debug("DLQ insert without returning ID");
            return -1;
        }
    }

    /**
     * Get messages ready for retry.
     */
    public List<DlqMessageDTO> findRetryable(int limit, int maxRetries) {
        String sql = """
            SELECT id, topic, message_key, payload, error_message, status, retry_count, created_at, next_retry_at
            FROM dlq 
            WHERE status = 'PENDING' 
              AND retry_count < ?
              AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
            ORDER BY created_at
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DlqMessageDTO(
                rs.getLong("id"),
                rs.getString("topic"),
                rs.getString("message_key"),
                rs.getString("payload"),
                rs.getString("error_message"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("next_retry_at") != null 
                        ? rs.getTimestamp("next_retry_at").toLocalDateTime() : null
        ), maxRetries, limit);
    }

    /**
     * Get messages ready for retry (default 3 max retries).
     */
    public List<DlqMessageDTO> findRetryable(int limit) {
        return findRetryable(limit, 3);
    }

    /**
     * Increment retry count and schedule next retry.
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
     * Mark as being processed (prevent duplicate processing).
     */
    @Transactional
    public boolean markProcessing(long id) {
        String sql = """
            UPDATE dlq 
            SET status = 'PROCESSING' 
            WHERE id = ? AND status = 'PENDING'
            """;
        return jdbcTemplate.update(sql, id) > 0;
    }

    /**
     * Mark message as successfully processed.
     */
    @Transactional
    public void markProcessed(long id) {
        String sql = "UPDATE dlq SET status = 'PROCESSED' WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    /**
     * Mark message as permanently failed.
     */
    @Transactional
    public void markFailed(long id, String reason) {
        String sql = """
            UPDATE dlq 
            SET status = 'FAILED', error_message = ?
            WHERE id = ?
            """;
        jdbcTemplate.update(sql, reason, id);
    }

    /**
     * Reset processing status back to pending (for stuck messages).
     */
    @Transactional
    public void resetToPending(long id) {
        String sql = "UPDATE dlq SET status = 'PENDING' WHERE id = ? AND status = 'PROCESSING'";
        jdbcTemplate.update(sql, id);
    }

    /**
     * Delete a DLQ entry.
     */
    @Transactional
    public void delete(long id) {
        jdbcTemplate.update("DELETE FROM dlq WHERE id = ?", id);
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
     * Count pending by topic.
     */
    public int countPendingByTopic(String topic) {
        String sql = "SELECT COUNT(*) FROM dlq WHERE status = 'PENDING' AND topic = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, topic);
        return count != null ? count : 0;
    }

    /**
     * Get total DLQ depth.
     */
    public int getDepth() {
        return countPending();
    }

    /**
     * Purge old processed/failed messages.
     */
    @Transactional
    public int purgeOlderThan(int days) {
        String sql = """
            DELETE FROM dlq 
            WHERE status IN ('PROCESSED', 'FAILED') 
              AND created_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * ?
            """;
        return jdbcTemplate.update(sql, days);
    }

    /**
     * Reset stuck processing messages (cleanup for crashed workers).
     */
    @Transactional
    public int resetStuckProcessing(int minutesStuck) {
        String sql = """
            UPDATE dlq 
            SET status = 'PENDING'
            WHERE status = 'PROCESSING'
              AND last_retry_at < CURRENT_TIMESTAMP - INTERVAL '1 minute' * ?
            """;
        return jdbcTemplate.update(sql, minutesStuck);
    }
}
