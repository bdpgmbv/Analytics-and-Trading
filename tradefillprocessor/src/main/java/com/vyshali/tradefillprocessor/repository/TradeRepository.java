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

    public boolean saveExecution(ExecutionReportDTO fill) {
        int rows = jdbcTemplate.update(TradeSql.INSERT_FILL, fill.execID(), fill.orderID(), fill.symbol(), fill.side(), fill.lastQty(), fill.lastPx());

        if (rows == 0) {
            log.warn("Duplicate Fill detected and ignored: ExecID={}", fill.execID());
            return false;
        }

        log.info("Persisted Fill: {}", fill.execID());
        return true;
    }
}