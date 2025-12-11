package com.vyshali.positionloader.repository;

/*
 * 12/11/2025 - 11:45 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.dto.Dto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
 * Replaces: PositionRepository, EodRepository, AuditRepository, ReferenceDataRepository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DataRepository {

    private final JdbcTemplate jdbc;

    // ═══════════════════════════════════════════════════════════════════════════
    // POSITION OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public int insertPositions(Integer accountId, List<Dto.Position> positions, String source, int batchId, LocalDate businessDate) {
        if (positions == null || positions.isEmpty()) return 0;

        String sql = """
                INSERT INTO positions (account_id, product_id, quantity, price, currency, business_date, batch_id, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (account_id, product_id, business_date) 
                DO UPDATE SET quantity = EXCLUDED.quantity, price = EXCLUDED.price, batch_id = EXCLUDED.batch_id, updated_at = NOW()
                """;

        for (Dto.Position pos : positions) {
            jdbc.update(sql, accountId, pos.productId(), pos.quantity(), pos.price(), pos.currency(), businessDate, batchId, source);
        }
        return positions.size();
    }

    public List<Dto.Position> getPositionsByDate(Integer accountId, LocalDate date) {
        return jdbc.query("SELECT product_id, quantity, price, currency FROM positions WHERE account_id = ? AND business_date = ?", (rs, rowNum) -> new Dto.Position(rs.getInt("product_id"), null, null, rs.getString("currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("price")), accountId, date);
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
    // EOD STATUS
    // ═══════════════════════════════════════════════════════════════════════════

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

    public void recordEodStart(Integer accountId, LocalDate date) {
        jdbc.update("""
                INSERT INTO eod_runs (account_id, business_date, status, started_at)
                VALUES (?, ?, 'RUNNING', NOW())
                ON CONFLICT (account_id, business_date) DO UPDATE SET status = 'RUNNING', started_at = NOW(), error_message = NULL
                """, accountId, date);
    }

    public void recordEodComplete(Integer accountId, LocalDate date, int positionCount) {
        jdbc.update("UPDATE eod_runs SET status = 'COMPLETED', completed_at = NOW(), position_count = ? WHERE account_id = ? AND business_date = ?", positionCount, accountId, date);
    }

    public void recordEodFailed(Integer accountId, LocalDate date, String error) {
        jdbc.update("UPDATE eod_runs SET status = 'FAILED', completed_at = NOW(), error_message = ? WHERE account_id = ? AND business_date = ?", error, accountId, date);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REFERENCE DATA
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void ensureReferenceData(Dto.AccountSnapshot snapshot) {
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
}
