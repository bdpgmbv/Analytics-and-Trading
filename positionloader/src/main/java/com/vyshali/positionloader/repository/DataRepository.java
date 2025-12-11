package com.vyshali.positionloader.repository;

import com.vyshali.positionloader.config.AppConfig.LoaderConfig;
import com.vyshali.positionloader.dto.Dto;
import com.vyshali.positionloader.service.AlertService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Data repository with all Phase 1-4 enhancements including archival.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DataRepository {

    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final LoaderConfig config;
    private final AlertService alertService;

    public static final String BATCH_STAGING = "STAGING";
    public static final String BATCH_ACTIVE = "ACTIVE";
    public static final String BATCH_ARCHIVED = "ARCHIVED";

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH OPERATIONS (Phase 2)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    @CircuitBreaker(name = "database")
    public BatchInsertResult insertPositionsToStaging(Integer accountId, List<Dto.Position> positions, String source, LocalDate businessDate) {
        if (positions == null || positions.isEmpty()) return new BatchInsertResult(0, 0, BATCH_STAGING);

        int batchId = getNextBatchId(accountId);
        createBatchRecord(accountId, batchId, businessDate, BATCH_STAGING, positions.size());

        jdbc.batchUpdate("""
                INSERT INTO positions (account_id, product_id, quantity, price, currency, business_date, batch_id, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, positions, config.batchSize(), (ps, pos) -> {
            ps.setInt(1, accountId);
            ps.setInt(2, pos.productId());
            ps.setBigDecimal(3, pos.quantity());
            ps.setBigDecimal(4, pos.price());
            ps.setString(5, pos.currency());
            ps.setObject(6, businessDate);
            ps.setInt(7, batchId);
            ps.setString(8, source);
        });

        metrics.counter("posloader.db.rows_inserted").increment(positions.size());
        return new BatchInsertResult(batchId, positions.size(), BATCH_STAGING);
    }

    @Transactional
    @CircuitBreaker(name = "database")
    public boolean activateBatch(Integer accountId, int batchId, LocalDate businessDate) {
        jdbc.update("UPDATE account_batches SET status = ?, archived_at = NOW() WHERE account_id = ? AND business_date = ? AND status = ?", BATCH_ARCHIVED, accountId, businessDate, BATCH_ACTIVE);
        int activated = jdbc.update("UPDATE account_batches SET status = ?, activated_at = NOW() WHERE account_id = ? AND batch_id = ? AND status = ?", BATCH_ACTIVE, accountId, batchId, BATCH_STAGING);
        metrics.counter("posloader.batch.activated").increment();
        return activated == 1;
    }

    @Transactional
    public boolean rollbackBatch(Integer accountId, LocalDate businessDate) {
        jdbc.update("UPDATE account_batches SET status = 'ROLLED_BACK' WHERE account_id = ? AND business_date = ? AND status = ?", accountId, businessDate, BATCH_ACTIVE);
        List<Integer> archived = jdbc.queryForList("SELECT batch_id FROM account_batches WHERE account_id = ? AND business_date = ? AND status = ? ORDER BY archived_at DESC LIMIT 1", Integer.class, accountId, businessDate, BATCH_ARCHIVED);
        if (archived.isEmpty()) return false;
        jdbc.update("UPDATE account_batches SET status = ? WHERE account_id = ? AND batch_id = ?", BATCH_ACTIVE, accountId, archived.get(0));
        return true;
    }

    private void createBatchRecord(Integer accountId, int batchId, LocalDate date, String status, int count) {
        jdbc.update("INSERT INTO account_batches (account_id, batch_id, business_date, status, position_count, created_at) VALUES (?, ?, ?, ?, ?, NOW()) ON CONFLICT (account_id, batch_id) DO UPDATE SET status = EXCLUDED.status", accountId, batchId, date, status, count);
    }

    public record BatchInsertResult(int batchId, int count, String status) {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #16: DUPLICATE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean isDuplicateSnapshot(Integer accountId, LocalDate date, String contentHash) {
        if (contentHash == null) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM snapshot_hashes WHERE account_id = ? AND business_date < ? AND content_hash = ? AND business_date > ?", Integer.class, accountId, date, contentHash, date.minusDays(7));
        return count != null && count > 0;
    }

    public void saveSnapshotHash(Integer accountId, LocalDate date, String contentHash) {
        if (contentHash == null) return;
        jdbc.update("INSERT INTO snapshot_hashes (account_id, business_date, content_hash, created_at) VALUES (?, ?, ?, NOW()) ON CONFLICT (account_id, business_date) DO UPDATE SET content_hash = EXCLUDED.content_hash", accountId, date, contentHash);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #20: MANUAL OVERRIDE
    // ═══════════════════════════════════════════════════════════════════════════

    public void updatePosition(Integer accountId, Integer productId, BigDecimal quantity, BigDecimal price, LocalDate date) {
        jdbc.update("UPDATE positions SET quantity = ?, price = ?, updated_at = NOW() WHERE account_id = ? AND product_id = ? AND business_date = ?", quantity, price, accountId, productId, date);
    }

    public void resetEodStatus(Integer accountId, LocalDate date) {
        jdbc.update("DELETE FROM eod_runs WHERE account_id = ? AND business_date = ?", accountId, date);
        jdbc.update("DELETE FROM Eod_Daily_Status WHERE account_id = ? AND business_date = ?", accountId, date);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 4 #22: DATA ARCHIVAL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Archive old positions to positions_archive table.
     * Keeps month-end snapshots in main table.
     */
    @Scheduled(cron = "${loader.archival.cron-schedule:0 0 2 * * SUN}")
    @Transactional
    public void archiveOldPositions() {
        if (!config.archival().enabled()) {
            log.debug("Archival disabled by config");
            return;
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(config.archival().retentionDays());
        log.info("Starting position archival for data older than {}", cutoffDate);
        Timer.Sample timer = Timer.start(metrics);

        try {
            // Move old positions to archive (except month-end)
            int archived = jdbc.update("""
                    INSERT INTO positions_archive 
                    SELECT * FROM positions 
                    WHERE business_date < ? 
                    AND EXTRACT(DAY FROM business_date) != EXTRACT(DAY FROM (DATE_TRUNC('MONTH', business_date) + INTERVAL '1 MONTH - 1 DAY'))
                    ON CONFLICT DO NOTHING
                    """, cutoffDate);

            // Delete archived positions from main table
            int deleted = jdbc.update("""
                    DELETE FROM positions 
                    WHERE business_date < ? 
                    AND EXTRACT(DAY FROM (DATE_TRUNC('MONTH', business_date) + INTERVAL '1 MONTH - 1 DAY')) != EXTRACT(DAY FROM business_date)
                    """, cutoffDate);

            // Archive old EOD runs
            jdbc.update("DELETE FROM eod_runs WHERE business_date < ?", cutoffDate.minusDays(30));

            // Archive old audit logs
            jdbc.update("DELETE FROM audit_logs WHERE created_at < ?", cutoffDate.minusDays(90));

            timer.stop(metrics.timer("posloader.archival.duration"));
            metrics.counter("posloader.archival.positions_archived").increment(archived);
            metrics.counter("posloader.archival.positions_deleted").increment(deleted);

            log.info("Archival complete: {} positions archived, {} deleted", archived, deleted);
            alertService.info("ARCHIVAL_COMPLETE", String.format("Archived %d positions, deleted %d", archived, deleted), "system");

        } catch (Exception e) {
            log.error("Archival failed: {}", e.getMessage());
            alertService.critical("ARCHIVAL_FAILED", e.getMessage(), "system");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STANDARD OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @CircuitBreaker(name = "database")
    public boolean isEodCompleted(Integer accountId, LocalDate date) {
        Dto.EodStatus s = getEodStatus(accountId, date);
        return s != null && "COMPLETED".equals(s.status());
    }

    public Dto.EodStatus getEodStatus(Integer accountId, LocalDate date) {
        List<Dto.EodStatus> r = jdbc.query("SELECT account_id, business_date, status, position_count, started_at, completed_at, error_message FROM eod_runs WHERE account_id = ? AND business_date = ? ORDER BY started_at DESC LIMIT 1", (rs, i) -> new Dto.EodStatus(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getString(3), rs.getInt(4), rs.getTimestamp(5) != null ? rs.getTimestamp(5).toLocalDateTime() : null, rs.getTimestamp(6) != null ? rs.getTimestamp(6).toLocalDateTime() : null, rs.getString(7)), accountId, date);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Dto.EodStatus> getEodHistory(Integer accountId, int days) {
        return jdbc.query("SELECT account_id, business_date, status, position_count, started_at, completed_at, error_message FROM eod_runs WHERE account_id = ? AND business_date >= CURRENT_DATE - INTERVAL '" + days + " days' ORDER BY business_date DESC", (rs, i) -> new Dto.EodStatus(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getString(3), rs.getInt(4), rs.getTimestamp(5) != null ? rs.getTimestamp(5).toLocalDateTime() : null, rs.getTimestamp(6) != null ? rs.getTimestamp(6).toLocalDateTime() : null, rs.getString(7)), accountId);
    }

    public void recordEodStart(Integer accountId, LocalDate date) {
        jdbc.update("INSERT INTO eod_runs (account_id, business_date, status, started_at) VALUES (?, ?, 'RUNNING', NOW()) ON CONFLICT (account_id, business_date) DO UPDATE SET status = 'RUNNING', started_at = NOW(), error_message = NULL", accountId, date);
    }

    public void recordEodComplete(Integer accountId, LocalDate date, int count) {
        jdbc.update("UPDATE eod_runs SET status = 'COMPLETED', completed_at = NOW(), position_count = ? WHERE account_id = ? AND business_date = ?", count, accountId, date);
    }

    public void recordEodFailed(Integer accountId, LocalDate date, String error) {
        jdbc.update("UPDATE eod_runs SET status = 'FAILED', completed_at = NOW(), error_message = ? WHERE account_id = ? AND business_date = ?", error, accountId, date);
    }

    public List<Dto.Position> getPositionsByDate(Integer accountId, LocalDate date) {
        return jdbc.query("SELECT product_id, quantity, price, currency FROM positions WHERE account_id = ? AND business_date = ?", (rs, i) -> new Dto.Position(rs.getInt(1), null, null, rs.getString(4), rs.getBigDecimal(2), rs.getBigDecimal(3)), accountId, date);
    }

    public List<Dto.Position> getActivePositions(Integer accountId, LocalDate date) {
        return jdbc.query("""
                SELECT p.product_id, p.quantity, p.price, p.currency FROM positions p
                JOIN account_batches b ON p.account_id = b.account_id AND p.batch_id = b.batch_id
                WHERE p.account_id = ? AND p.business_date = ? AND b.status = 'ACTIVE'
                """, (rs, i) -> new Dto.Position(rs.getInt(1), null, null, rs.getString(4), rs.getBigDecimal(2), rs.getBigDecimal(3)), accountId, date);
    }

    public List<Integer> getAccountsWithPositions(LocalDate date) {
        return jdbc.queryForList("SELECT DISTINCT account_id FROM positions WHERE business_date = ?", Integer.class, date);
    }

    public int getNextBatchId(Integer accountId) {
        Integer r = jdbc.queryForObject("SELECT COALESCE(MAX(batch_id), 0) + 1 FROM positions WHERE account_id = ?", Integer.class, accountId);
        return r != null ? r : 1;
    }

    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, LocalDate date) {
        List<BigDecimal> r = jdbc.queryForList("SELECT quantity FROM positions WHERE account_id = ? AND product_id = ? AND business_date <= ? ORDER BY business_date DESC LIMIT 1", BigDecimal.class, accountId, productId, date);
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    @Transactional
    public int insertPositions(Integer accountId, List<Dto.Position> positions, String source, int batchId, LocalDate date) {
        if (positions == null || positions.isEmpty()) return 0;
        jdbc.batchUpdate("INSERT INTO positions (account_id, product_id, quantity, price, currency, business_date, batch_id, source, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW()) ON CONFLICT (account_id, product_id, business_date) DO UPDATE SET quantity = EXCLUDED.quantity, price = EXCLUDED.price", positions, config.batchSize(), (ps, pos) -> {
            ps.setInt(1, accountId);
            ps.setInt(2, pos.productId());
            ps.setBigDecimal(3, pos.quantity());
            ps.setBigDecimal(4, pos.price());
            ps.setString(5, pos.currency());
            ps.setObject(6, date);
            ps.setInt(7, batchId);
            ps.setString(8, source);
        });
        return positions.size();
    }

    @Transactional
    public void ensureReferenceData(Dto.AccountSnapshot s) {
        jdbc.update("INSERT INTO Clients (client_id, client_name, status, updated_at) VALUES (?, ?, 'ACTIVE', NOW()) ON CONFLICT (client_id) DO UPDATE SET client_name = EXCLUDED.client_name", s.clientId(), s.clientName());
        jdbc.update("INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW()) ON CONFLICT (fund_id) DO UPDATE SET fund_name = EXCLUDED.fund_name", s.fundId(), s.clientId(), s.fundName(), s.baseCurrency());
        jdbc.update("INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name, base_currency, account_number) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (account_id) DO NOTHING", s.accountId(), s.clientId(), s.clientName(), s.fundId(), s.fundName(), s.baseCurrency(), s.accountNumber());
        if (s.positions() != null) {
            for (Dto.Position p : s.positions()) {
                jdbc.update("INSERT INTO Products (product_id, ticker, asset_class) VALUES (?, ?, ?) ON CONFLICT (product_id) DO UPDATE SET ticker = EXCLUDED.ticker", p.productId(), p.ticker(), p.assetClass());
            }
        }
    }

    @Cacheable(value = "clientAccounts", key = "#clientId")
    public int countClientAccounts(Integer clientId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM Accounts WHERE fund_id IN (SELECT fund_id FROM Funds WHERE client_id = ?)", Integer.class, clientId);
        return c != null ? c : 0;
    }

    public boolean isClientComplete(Integer clientId, LocalDate date) {
        int total = countClientAccounts(clientId);
        if (total == 0) return false;
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM Eod_Daily_Status WHERE client_id = ? AND business_date = ? AND status = 'COMPLETED'", Integer.class, clientId, date);
        return done != null && done >= total;
    }

    public void log(String type, String entityId, String actor, String payload) {
        jdbc.update("INSERT INTO Audit_Logs (event_type, entity_id, actor, payload, created_at) VALUES (?, ?, ?, ?, NOW())", type, entityId, actor, payload);
    }

    public void markAccountComplete(Integer accountId, Integer clientId, LocalDate date) {
        jdbc.update("INSERT INTO Eod_Daily_Status (account_id, client_id, business_date, status, completed_at) VALUES (?, ?, ?, 'COMPLETED', NOW()) ON CONFLICT (account_id, business_date) DO UPDATE SET status = 'COMPLETED'", accountId, clientId, date);
    }

    public void saveToDlq(String topic, String key, String payload, String error) {
        jdbc.update("INSERT INTO dlq (topic, message_key, payload, error_message, created_at) VALUES (?, ?, ?, ?, NOW())", topic, key, payload, error);
    }

    public List<Map<String, Object>> getDlqMessages(int limit) {
        return jdbc.queryForList("SELECT id, topic, message_key, payload, retry_count FROM dlq WHERE retry_count < ? ORDER BY created_at LIMIT ?", config.dlqMaxRetries(), limit);
    }

    public void deleteDlq(Long id) {
        jdbc.update("DELETE FROM dlq WHERE id = ?", id);
    }

    public void incrementDlqRetry(Long id) {
        jdbc.update("UPDATE dlq SET retry_count = retry_count + 1 WHERE id = ?", id);
    }

    public int getDlqDepth() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dlq WHERE retry_count < ?", Integer.class, config.dlqMaxRetries());
        return c != null ? c : 0;
    }
}