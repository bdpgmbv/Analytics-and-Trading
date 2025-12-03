package com.vyshali.tradefillprocessor.service;

/*
 * 12/03/2025 - 1:15 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.ExecutionReportDTO;
import com.vyshali.tradefillprocessor.dto.OrderStateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradePersistenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void saveFill(ExecutionReportDTO fill) {
        String sql = """
                    INSERT INTO Execution_Fills (exec_id, order_id, fill_qty, fill_price, fill_time, venue)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """;
        jdbcTemplate.update(sql, fill.execId(), fill.orderId(), fill.lastQty(), fill.lastPx(), fill.venue());
    }

    @Transactional
    public void updateOrderSummary(OrderStateDTO state) {
        BigDecimal avgPx = (state.totalFilledQty().compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : state.totalNotional().divide(state.totalFilledQty(), 6, RoundingMode.HALF_UP);

        String sql = """
                    INSERT INTO Client_Orders (order_id, account_id, ticker, side, status, filled_qty, avg_price)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (order_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        filled_qty = EXCLUDED.filled_qty,
                        avg_price = EXCLUDED.avg_price,
                        updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(sql, state.orderId(), state.accountId(), state.ticker(), state.side(), state.status(), state.totalFilledQty(), avgPx);
    }
}
