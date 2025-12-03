package com.vyshali.hedgeservice.service;

/*
 * 12/03/2025 - 12:13 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HedgeAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    // 1. Transactions Grid
    public List<TransactionViewDTO> getTransactions(Integer accountId) {
        String sql = """
                    SELECT 
                        'MSPA' as source, a.account_number,
                        p.identifier_type, p.identifier_value, p.security_description,
                        p.issue_currency, p.settlement_currency,
                        CASE WHEN t.quantity > 0 THEN 'L' ELSE 'S' END as long_short,
                        CASE WHEN t.txn_type = 'BUY' THEN 'B' ELSE 'S' END as buy_sell,
                        p.asset_class as position_type,
                        t.quantity, t.cost_local, t.cost_settle
                    FROM Transactions t
                    JOIN Products p ON t.product_id = p.product_id
                    JOIN Accounts a ON t.account_id = a.account_id
                    WHERE t.account_id = ?
                    ORDER BY t.trade_date DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TransactionViewDTO(rs.getString("source"), rs.getString("account_number"), rs.getString("identifier_type"), rs.getString("identifier_value"), rs.getString("security_description"), rs.getString("issue_currency"), rs.getString("settlement_currency"), rs.getString("long_short"), rs.getString("buy_sell"), rs.getString("position_type"), rs.getBigDecimal("quantity"), rs.getBigDecimal("cost_local"), rs.getBigDecimal("cost_settle")), accountId);
    }

    // 2. Position Upload Grid (View)
    public List<PositionUploadDTO> getPositionUploadView(Integer accountId) {
        String sql = """
                    SELECT 
                        pos.source_system, a.account_number,
                        p.identifier_type, p.identifier_value, p.security_description,
                        p.issue_currency, p.settlement_currency, p.asset_class,
                        pos.quantity, pos.market_value_base
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    JOIN Accounts a ON pos.account_id = a.account_id
                    WHERE pos.account_id = ? AND pos.source_system = 'MANUAL'
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PositionUploadDTO(rs.getString("source_system"), rs.getString("account_number"), rs.getString("identifier_type"), rs.getString("identifier_value"), rs.getString("security_description"), rs.getString("issue_currency"), rs.getString("settlement_currency"), rs.getString("asset_class"), rs.getBigDecimal("quantity"), rs.getBigDecimal("market_value_base")), accountId);
    }

    // 3. Security Exposure Grid
    public List<HedgePositionDTO> getHedgePositions(Integer accountId) {
        String sql = """
                    SELECT 
                        p.identifier_type, p.identifier_value, p.asset_class, p.issue_currency,
                        MAX(CASE WHEN pe.exposure_type = 'GENERIC' THEN pe.currency END) as gen_exp_ccy,
                        MAX(CASE WHEN pe.exposure_type = 'GENERIC' THEN pe.weight END) as gen_exp_weight,
                        MAX(CASE WHEN pe.exposure_type = 'SPECIFIC_1' THEN pe.currency END) as spec_exp_ccy,
                        MAX(CASE WHEN pe.exposure_type = 'SPECIFIC_1' THEN pe.weight END) as spec_exp_weight,
                        pos.quantity, pos.market_value_base, pos.cost_local
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    LEFT JOIN Position_Exposures pe ON pos.position_id = pe.position_id
                    WHERE pos.account_id = ?
                    GROUP BY pos.position_id, p.identifier_type, p.identifier_value, p.asset_class, p.issue_currency, pos.quantity, pos.market_value_base, pos.cost_local
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new HedgePositionDTO(rs.getString("identifier_type"), rs.getString("identifier_value"), rs.getString("asset_class"), rs.getString("issue_currency"), rs.getString("gen_exp_ccy"), rs.getBigDecimal("gen_exp_weight"), rs.getString("spec_exp_ccy"), rs.getBigDecimal("spec_exp_weight"), BigDecimal.ONE, BigDecimal.ZERO, // Real-time Price/FX handled by UI via WebSocket
                rs.getBigDecimal("quantity"), rs.getBigDecimal("market_value_base"), rs.getBigDecimal("cost_local")), accountId);
    }

    // 4. Forward Maturity Alert
    public List<ForwardMaturityDTO> getForwardMaturityAlerts(Integer accountId) {
        String sql = """
                    SELECT 
                        p.issue_currency, SUM(pos.quantity) as current_notional,
                        SUM(pos.market_value_base) as notional_hedge_ccy, t.value_date
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    JOIN Transactions t ON pos.account_id = t.account_id AND pos.product_id = t.product_id
                    WHERE pos.account_id = ? AND p.asset_class = 'FX_FORWARD'
                    GROUP BY p.issue_currency, t.value_date
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ForwardMaturityDTO(rs.getString("issue_currency"), rs.getBigDecimal("current_notional"), rs.getBigDecimal("current_notional"), rs.getBigDecimal("notional_hedge_ccy"), rs.getDate("value_date").toLocalDate()), accountId);
    }

    // 5. Cash Management
    public List<CashManagementDTO> getCashManagement(Integer fundId) {
        String sql = """
                    SELECT 
                        p.issue_currency, SUM(pos.quantity) as cash_balance,
                        SUM(pos.market_value_base) as unhedged_exposure
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    JOIN Accounts a ON pos.account_id = a.account_id
                    WHERE a.fund_id = ? AND p.asset_class = 'CASH'
                    GROUP BY p.issue_currency
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CashManagementDTO(rs.getString("issue_currency"), rs.getBigDecimal("cash_balance"), rs.getBigDecimal("unhedged_exposure"), "SPOT", BigDecimal.ZERO, BigDecimal.ZERO, java.time.LocalDate.now().plusDays(2)), fundId);
    }

    // --- WRITE ACTION: Save Manual Position ---
    @Transactional
    public void saveManualPosition(ManualPositionInputDTO input) {
        // 1. Lookup Product
        String lookupSql = "SELECT product_id FROM Products WHERE ticker = ?";
        Integer productId;
        try {
            productId = jdbcTemplate.queryForObject(lookupSql, Integer.class, input.ticker());
        } catch (Exception e) {
            throw new RuntimeException("Ticker not found: " + input.ticker());
        }

        // 2. Upsert
        String upsertSql = """
                    INSERT INTO Positions (account_id, product_id, quantity, source_system, position_type, updated_at)
                    VALUES (?, ?, ?, 'MANUAL', 'PHYSICAL', CURRENT_TIMESTAMP)
                    ON CONFLICT (account_id, product_id) 
                    DO UPDATE SET 
                        quantity = EXCLUDED.quantity,
                        source_system = 'MANUAL',
                        updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(upsertSql, input.accountId(), productId, input.quantity());
        log.info("Manual Position saved: Account={} Ticker={}", input.accountId(), input.ticker());
    }
}
