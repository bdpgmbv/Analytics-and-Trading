package com.vyshali.hedgeservice.repository;

/*
 * 12/09/2025 - 1:46 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.hedgeservice.dto.CashManagementDTO;
import com.vyshali.hedgeservice.dto.ForwardMaturityDTO;
import com.vyshali.hedgeservice.dto.HedgeExecutionRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HedgeRepository {

    private final JdbcTemplate jdbcTemplate;

    // --- WRITE OPERATIONS (Order Tracking) ---

    public String createOrder(HedgeExecutionRequestDTO req) {
        String internalId = UUID.randomUUID().toString();
        String sql = """
                    INSERT INTO Hedge_Orders (order_id, account_id, currency_pair, side, quantity, tenor, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """;
        jdbcTemplate.update(sql, internalId, req.accountId(), req.currencyPair(), req.side(), req.quantity(), req.tenor());
        return internalId;
    }

    public void updateOrderStatus(String internalId, String clOrdId, String status) {
        String sql = "UPDATE Hedge_Orders SET status = ?, cl_ord_id = ?, updated_at = CURRENT_TIMESTAMP WHERE order_id = ?";
        jdbcTemplate.update(sql, status, clOrdId, internalId);
    }

    // --- READ OPERATIONS (Analytics) ---

    public List<ForwardMaturityDTO> findForwardMaturityAlerts(Integer accountId) {
        // Logic: Find Forwards maturing in next 30 days
        // Note: Assumes Transactions table has 'maturity_date' as per schema 001/002
        String sql = """
                    SELECT 
                        t.product_id, 
                        p.issue_currency as currency,
                        SUM(t.quantity) as current_notional,
                        t.maturity_date
                    FROM Transactions t
                    JOIN Products p ON t.product_id = p.product_id
                    WHERE t.account_id = ? 
                      AND t.txn_type = 'FX_FORWARD'
                      AND t.maturity_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + INTERVAL '30 days')
                    GROUP BY t.product_id, p.issue_currency, t.maturity_date
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ForwardMaturityDTO(rs.getString("currency"), rs.getBigDecimal("current_notional"), BigDecimal.ZERO, // Unhedged placeholder
                rs.getBigDecimal("current_notional").negate(), // Suggestion: Hedge by selling
                rs.getDate("maturity_date").toLocalDate()), accountId);
    }

    public List<CashManagementDTO> findCashBalances(Integer fundId) {
        // Logic: Aggregate 'CASH' positions by Currency for a specific Fund
        String sql = """
                    SELECT 
                        p.issue_currency as currency,
                        SUM(pos.quantity) as cash_balance
                    FROM Positions pos
                    JOIN Products p ON pos.product_id = p.product_id
                    JOIN Accounts a ON pos.account_id = a.account_id
                    WHERE a.fund_id = ? 
                      AND p.asset_class = 'CASH'
                      AND pos.batch_id = (SELECT batch_id FROM Account_Batches ab WHERE ab.account_id = pos.account_id AND ab.status = 'ACTIVE')
                    GROUP BY p.issue_currency
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CashManagementDTO(rs.getString("currency"), rs.getBigDecimal("cash_balance"), BigDecimal.ZERO, // Unhedged calculation requires complex logic
                "SPOT", rs.getBigDecimal("cash_balance").negate(), // Suggestion: Flatten to zero
                BigDecimal.ZERO, LocalDate.now().plusDays(2)), fundId);
    }
}
