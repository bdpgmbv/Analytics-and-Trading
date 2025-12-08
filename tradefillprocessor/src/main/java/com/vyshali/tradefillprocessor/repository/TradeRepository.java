package com.vyshali.tradefillprocessor.repository;

/*
 * 12/03/2025 - 1:16 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.ExecutionReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TradeRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Saves a Trade Fill execution.
     * Uses 'ON CONFLICT DO NOTHING' to ensure idempotency if the upstream
     * (Exchange/FIX Engine) sends duplicate messages.
     *
     * @param fill The execution report from the exchange
     * @return true if inserted, false if it was a duplicate
     */
    public boolean saveExecution(ExecutionReportDTO fill) {
        String sql = """
                    INSERT INTO Trade_Fills (fill_id, order_id, symbol, side, fill_qty, fill_price, processed)
                    VALUES (?, ?, ?, ?, ?, ?, FALSE)
                    ON CONFLICT (fill_id) DO NOTHING
                """;

        // 'fill_id' maps to the Exchange's unique ExecutionID (Tag 17).
        // This is globally unique per fill event.
        int rows = jdbcTemplate.update(sql, fill.execID(), fill.orderID(), fill.symbol(), fill.side(), fill.lastQty(), fill.lastPx());

        if (rows == 0) {
            log.warn("Duplicate Fill detected and ignored: ExecID={}", fill.execID());
            return false;
        }

        log.info("Persisted Fill: {}", fill.execID());
        return true;
    }
}