package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.dto.Dto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Single repository for all database operations.
 * <p>
 * Phase 1 Enhancements: Metrics, config externalization
 * Phase 2 Enhancements:
 * - #6 Batch Switching: STAGING → ACTIVE pattern with rollback support
 * - #7 DB Circuit Breaker: @CircuitBreaker on critical operations
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DataRepository {

    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final LoaderConfig config;

    // Batch statuses
    public static final String BATCH_STAGING = "STAGING";
    public static final String BATCH_ACTIVE = "ACTIVE";
    public static final String BATCH_ARCHIVED = "ARCHIVED";
    public static final String BATCH_FAILED = "FAILED";

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2 ENHANCEMENT #6: BATCH SWITCHING (Blue/Green)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Insert positions to a STAGING batch. Does NOT make them active yet.
     * Call activateBatch() after validation to switch atomically.
     */
    @Transactional
    @CircuitBreaker(name = "database", fallbackMethod = "insertPositionsFallback")
    public BatchInsertResult insertPositionsToStaging(Integer accountId, List<Dto.Position> positions, String source, LocalDate businessDate) {
        if (positions == null || positions.isEmpty()) {
            return new BatchInsertResult(0, 0, BATCH_STAGING);
        }

        Timer.Sample timer = Timer.start(metrics);

        // Get next batch ID and create batch record
        int batchId = getNextBatchId(accountId);
        createBatchRecord(accountId, batchId, businessDate, BATCH_STAGING, positions.size());

        String sql = """
                INSERT INTO positions (account_id, product_id, quantity, price, currency, business_date, batch_id, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;

        jdbc.batchUpdate(sql, positions, config.batchSize(), (ps, pos) -> {
            ps.setInt(1, accountId);
            ps.setInt(2, pos.productId());
            ps.setBigDecimal(3, pos.quantity());
            ps.setBigDecimal(4, pos.price());
            ps.setString(5, pos.currency());
            ps.setObject(6, businessDate);
            ps.setInt(7, batchId);
            ps.setString(8, source);
        });

        timer.stop(metrics.timer("posloader.db.insert_positions"));
        metrics.counter("posloader.db.rows_inserted").increment(positions.size());

        log.debug("Inserted {} positions to STAGING batch {} for account {}", positions.size(), batchId, accountId);
        return new BatchInsertResult(batchId, positions.size(), BATCH_STAGING);
    }

    /**
     * Atomically activate a staging batch:
     * 1. Archive current ACTIVE batch (if exists)
     * 2. Set new batch to ACTIVE
     * <p>
     * This is the "switch" in blue/green deployment.
     */
    @Transactional
    @CircuitBreaker(name = "database", fallbackMethod = "activateBatchFallback")
    public boolean activateBatch(Integer accountId, int batchId, LocalDate businessDate) {
        Timer.Sample timer = Timer.start(metrics);

        try {
            // Step 1: Archive current ACTIVE batch for this account/date
            int archived = jdbc.update("""
                    UPDATE account_batches 
                    SET status = ?, archived_at = NOW()
                    WHERE account_id = ? AND business_date = ? AND status = ?
                    """, BATCH_ARCHIVED, accountId, businessDate, BATCH_ACTIVE);

            if (archived > 0) {
                log.debug("Archived {} previous ACTIVE batch(es) for account {}", archived, accountId);
            }

            // Step 2: Activate the new batch
            int activated = jdbc.update("""
                    UPDATE account_batches 
                    SET status = ?, activated_at = NOW()
                    WHERE account_id = ? AND batch_id = ? AND status = ?
                    """, BATCH_ACTIVE, accountId, batchId, BATCH_STAGING);

            if (activated != 1) {
                log.error("Failed to activate batch {} for account {} - batch not found or wrong status", batchId, accountId);
                metrics.counter("posloader.batch.activation_failed").increment();
                return false;
            }

            timer.stop(metrics.timer("posloader.db.batch_activation"));
            metrics.counter("posloader.batch.activated").increment();
            log.info("Activated batch {} for account {} (archived {} previous)", batchId, accountId, archived);
            return true;

        } catch (Exception e) {
            log.error("Batch activation failed for account {}, batch {}: {}", accountId, batchId, e.getMessage());
            // Mark batch as failed
            markBatchFailed(accountId, batchId, e.getMessage());
            throw e;
        }
    }

    /**
     * Rollback to previous batch:
     * 1. Deactivate current ACTIVE batch
     * 2. Reactivate most recent ARCHIVED batch
     */
    @Transactional
    @CircuitBreaker(name = "database")
    public boolean rollbackBatch(Integer accountId, LocalDate businessDate) {
        log.warn("Rolling back batch for account {} on {}", accountId, businessDate);

        // Step 1: Deactivate current
        jdbc.update("""
                UPDATE account_batches 
                SET status = 'ROLLED_BACK', archived_at = NOW()
                WHERE account_id = ? AND business_date = ? AND status = ?
                """, accountId, businessDate, BATCH_ACTIVE);

        // Step 2: Find and reactivate most recent archived batch
        List<Integer> archivedBatches = jdbc.queryForList("""
                SELECT batch_id FROM account_batches 
                WHERE account_id = ? AND business_date = ? AND status = ?
                ORDER BY archived_at DESC LIMIT 1
                """, Integer.class, accountId, businessDate, BATCH_ARCHIVED);

        if (archivedBatches.isEmpty()) {
            log.warn("No archived batch to rollback to for account {}", accountId);
            return false;
        }

        int previousBatchId = archivedBatches.get(0);
        jdbc.update("""
                UPDATE account_batches 
                SET status = ?, activated_at = NOW()
                WHERE account_id = ? AND batch_id = ?
                """, BATCH_ACTIVE, accountId, previousBatchId);

        metrics.counter("posloader.batch.rollback").increment();
        log.info("Rolled back account {} to batch {}", accountId, previousBatchId);
        return true;
    }

    /**
     * Create batch tracking record.
     */
    private void createBatchRecord(Integer accountId, int batchId, LocalDate businessDate, String status, int positionCount) {
        jdbc.update("""
                INSERT INTO account_batches (account_id, batch_id, business_date, status, position_count, created_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (account_id, batch_id) 
                DO UPDATE SET status = EXCLUDED.status, position_count = EXCLUDED.position_count
                """, accountId, batchId, businessDate, status, positionCount);
    }

    private void markBatchFailed(Integer accountId, int batchId, String error) {
        jdbc.update("""
                UPDATE account_batches 
                SET status = ?, error_message = ?
                WHERE account_id = ? AND batch_id = ?
                """, BATCH_FAILED, error, accountId, batchId);
    }

    public record BatchInsertResult(int batchId, int count, String status) {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY INSERT (for backward compatibility / simple cases)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Direct insert with upsert - simpler but no rollback capability.
     * Use insertPositionsToStaging + activateBatch for production EOD.
     */
    @Transactional
    @CircuitBreaker(name = "database", fallbackMethod = "insertPositionsFallback")
    public int insertPositions(Integer accountId, List<Dto.Position> positions, String source, int batchId, LocalDate businessDate) {
        if (positions == null || positions.isEmpty()) return 0;

        Timer.Sample timer = Timer.start(metrics);

        String sql = """
                INSERT INTO positions (account_id, product_id, quantity, price, currency, business_date, batch_id, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (account_id, product_id, business_date) 
                DO UPDATE SET quantity = EXCLUDED.quantity, price = EXCLUDED.price, batch_id = EXCLUDED.batch_id, updated_at = NOW()
                """;

        jdbc.batchUpdate(sql, positions, config.batchSize(), (ps, pos) -> {
            ps.setInt(1, accountId);
            ps.setInt(2, pos.productId());
            ps.setBigDecimal(3, pos.quantity());
            ps.setBigDecimal(4, pos.price());
            ps.setString(5, pos.currency());
            ps.setObject(6, businessDate);
            ps.setInt(7, batchId);
            ps.setString(8, source);
        });

        timer.stop(metrics.timer("posloader.db.insert_positions"));
        metrics.counter("posloader.db.rows_inserted").increment(positions.size());

        return positions.size();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2 ENHANCEMENT #7: CIRCUIT BREAKER FALLBACKS
    // ═══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unused")
    private BatchInsertResult insertPositionsFallback(Integer accountId, List<Dto.Position> positions, String source, LocalDate businessDate, Exception e) {
        log.error("Circuit breaker open for insertPositions - account {}: {}", accountId, e.getMessage());
        metrics.counter("posloader.circuit.database.rejected").increment();
        throw new RuntimeException("Database circuit open - cannot insert positions", e);
    }

    @SuppressWarnings("unused")
    private int insertPositionsFallback(Integer accountId, List<Dto.Position> positions, String source, int batchId, LocalDate businessDate, Exception e) {
        log.error("Circuit breaker open for insertPositions - account {}: {}", accountId, e.getMessage());
        metrics.counter("posloader.circuit.database.rejected").increment();
        throw new RuntimeException("Database circuit open - cannot insert positions", e);
    }

    @SuppressWarnings("unused")
    private boolean activateBatchFallback(Integer accountId, int batchId, LocalDate businessDate, Exception e) {
        log.error("Circuit breaker open for activateBatch - account {}: {}", accountId, e.getMessage());
        metrics.counter("posloader.circuit.database.rejected").increment();
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EOD STATUS (with circuit breaker)
    // ═══════════════════════════════════════════════════════════════════════════

    @CircuitBreaker(name = "database")
    public boolean isEodCompleted(Integer accountId, LocalDate date) {
        Dto.EodStatus status = getEodStatus(accountId, date);
        return status != null && "COMPLETED".equals(status.status());
    }

    @CircuitBreaker(name = "database")
    public Dto.EodStatus getEodStatus(Integer accountId, LocalDate date) {
        List<Dto.EodStatus> results = jdbc.query("""
                SELECT account_id, business_date, status, position_count, started_at, completed_at, error_message
                FROM eod_runs WHERE account_id = ? AND business_date = ? ORDER BY started_at DESC LIMIT 1
                """, (rs, rowNum) -> new Dto.EodStatus(rs.getInt("account_id"), rs.getDate("business_date").toLocalDate(), rs.getString("status"), rs.getInt("position_count"), rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toLocalDateTime() : null, rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toLocalDateTime() : null, rs.getString("error_message")), accountId, date);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Dto.EodStatus> getEodHistory(Integer accountId, int days) {
        return jdbc.query("""
                SELECT account_id, business_date, status, position_count, started_at, completed_at, error_message
                FROM eod_runs WHERE account_id = ? AND business_date >= CURRENT_DATE - INTERVAL '%d days'
                ORDER BY business_date DESC
                """.formatted(days), (rs, rowNum) -> new Dto.EodStatus(rs.getInt("account_id"), rs.getDate("business_date").toLocalDate(), rs.getString("status"), rs.getInt("position_count"), rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toLocalDateTime() : null, rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toLocalDateTime() : null, rs.getString("error_message")), accountId);
    }

    @CircuitBreaker(name = "database")
    public void recordEodStart(Integer accountId, LocalDate date) {
        Timer.Sample timer = Timer.start(metrics);
        jdbc.update("""
                INSERT INTO eod_runs (account_id, business_date, status, started_at)
                VALUES (?, ?, 'RUNNING', NOW())
                ON CONFLICT (account_id, business_date) DO UPDATE SET status = 'RUNNING', started_at = NOW(), error_message = NULL
                """, accountId, date);
        timer.stop(metrics.timer("posloader.db.eod_status_update"));
    }

    public void recordEodComplete(Integer accountId, LocalDate date, int positionCount) {
        jdbc.update("UPDATE eod_runs SET status = 'COMPLETED', completed_at = NOW(), position_count = ? WHERE account_id = ? AND business_date = ?", positionCount, accountId, date);
    }

    public void recordEodFailed(Integer accountId, LocalDate date, String error) {
        jdbc.update("UPDATE eod_runs SET status = 'FAILED', completed_at = NOW(), error_message = ? WHERE account_id = ? AND business_date = ?", error, accountId, date);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION QUERIES
    // ═══════════════════════════════════════════════════════════════════════════

    public List<Dto.Position> getPositionsByDate(Integer accountId, LocalDate date) {
        return jdbc.query("""
                SELECT product_id, quantity, price, currency 
                FROM positions 
                WHERE account_id = ? AND business_date = ?
                """, (rs, rowNum) -> new Dto.Position(rs.getInt("product_id"), null, null, rs.getString("currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("price")), accountId, date);
    }

    /**
     * Get positions from ACTIVE batch only (for accurate reads).
     */
    public List<Dto.Position> getActivePositions(Integer accountId, LocalDate date) {
        return jdbc.query("""
                SELECT p.product_id, p.quantity, p.price, p.currency 
                FROM positions p
                JOIN account_batches b ON p.account_id = b.account_id AND p.batch_id = b.batch_id
                WHERE p.account_id = ? AND p.business_date = ? AND b.status = 'ACTIVE'
                """, (rs, rowNum) -> new Dto.Position(rs.getInt("product_id"), null, null, rs.getString("currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("price")), accountId, date);
    }

    public int getNextBatchId(Integer accountId) {
        Integer result = jdbc.queryForObject("SELECT COALESCE(MAX(batch_id), 0) + 1 FROM positions WHERE account_id = ?", Integer.class, accountId);
        return result != null ? result : 1;
    }

    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, LocalDate date) {
        List<BigDecimal> results = jdbc.queryForList("SELECT quantity FROM positions WHERE account_id = ? AND product_id = ? AND business_date <= ? ORDER BY business_date DESC LIMIT 1", BigDecimal.class, accountId, productId, date);
        return results.isEmpty() ? BigDecimal.ZERO : results.get(0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REFERENCE DATA (with circuit breaker)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    @CircuitBreaker(name = "database")
    public void ensureReferenceData(Dto.AccountSnapshot snapshot) {
        Timer.Sample timer = Timer.start(metrics);

        // Client
        jdbc.update("""
                INSERT INTO Clients (client_id, client_name, status, updated_at) VALUES (?, ?, 'ACTIVE', NOW())
                ON CONFLICT (client_id) DO UPDATE SET client_name = EXCLUDED.client_name, updated_at = NOW()
                """, snapshot.clientId(), snapshot.clientName());

        // Fund
        jdbc.update("""
                INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW())
                ON CONFLICT (fund_id) DO UPDATE SET fund_name = EXCLUDED.fund_name, updated_at = NOW()
                """, snapshot.fundId(), snapshot.clientId(), snapshot.fundName(), snapshot.baseCurrency());

        // Account
        jdbc.update("""
                INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name, base_currency, account_number)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET account_number = EXCLUDED.account_number
                """, snapshot.accountId(), snapshot.clientId(), snapshot.clientName(), snapshot.fundId(), snapshot.fundName(), snapshot.baseCurrency(), snapshot.accountNumber());

        // Products
        if (snapshot.positions() != null) {
            for (Dto.Position p : snapshot.positions()) {
                jdbc.update("""
                        INSERT INTO Products (product_id, ticker, asset_class) VALUES (?, ?, ?)
                        ON CONFLICT (product_id) DO UPDATE SET ticker = EXCLUDED.ticker
                        """, p.productId(), p.ticker(), p.assetClass());
            }
        }

        timer.stop(metrics.timer("posloader.db.ensure_reference_data"));
    }

    @Cacheable(value = "clientAccounts", key = "#clientId")
    public int countClientAccounts(Integer clientId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM Accounts WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)", Integer.class, clientId);
        return count != null ? count : 0;
    }

    public boolean isClientComplete(Integer clientId, LocalDate date) {
        int total = countClientAccounts(clientId);
        if (total == 0) return false;
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM Eod_Daily_Status WHERE client_id = ? AND business_date = ? AND status = 'COMPLETED'", Integer.class, clientId, date);
        return done != null && done >= total;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT
    // ═══════════════════════════════════════════════════════════════════════════

    public void log(String eventType, String entityId, String actor, String payload) {
        jdbc.update("INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at) VALUES (?, ?, ?, ?, NOW())", eventType, entityId, actor, payload);
    }

    public void markAccountComplete(Integer accountId, Integer clientId, LocalDate date) {
        jdbc.update("""
                INSERT INTO Eod_Daily_Status (account_id, client_id, business_date, status, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', NOW())
                ON CONFLICT (account_id, business_date) DO UPDATE SET status = 'COMPLETED', completed_at = NOW()
                """, accountId, clientId, date);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEAD LETTER QUEUE
    // ═══════════════════════════════════════════════════════════════════════════

    public void saveToDlq(String topic, String key, String payload, String error) {
        jdbc.update("""
                INSERT INTO dlq (topic, message_key, payload, error_message, created_at)
                VALUES (?, ?, ?, ?, NOW())
                """, topic, key, payload, error);
        metrics.counter("posloader.dlq.messages_saved").increment();
    }

    public List<Map<String, Object>> getDlqMessages(int limit) {
        return jdbc.queryForList("""
                SELECT id, topic, message_key, payload, retry_count 
                FROM dlq 
                WHERE retry_count < ? 
                ORDER BY created_at 
                LIMIT ?
                """, config.dlqMaxRetries(), limit);
    }

    public void deleteDlq(Long id) {
        jdbc.update("DELETE FROM dlq WHERE id = ?", id);
    }

    public void incrementDlqRetry(Long id) {
        jdbc.update("UPDATE dlq SET retry_count = retry_count + 1, last_retry_at = NOW() WHERE id = ?", id);
    }

    public int getDlqDepth() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dlq WHERE retry_count < ?", Integer.class, config.dlqMaxRetries());
        return count != null ? count : 0;
    }
}