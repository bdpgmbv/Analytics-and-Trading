package com.vyshali.tradefillprocessor.repository;

/*
 * 12/10/2025 - FIXED: Accessor names to match ExecutionReportDTO record
 * - fill.execID() -> fill.execId()
 * - fill.orderID() -> fill.orderId()
 * - fill.symbol() -> fill.ticker()
 * Also added missing getFillsForOrder() method
 *
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.tradefillprocessor.dto.ExecutionReportDTO;
import com.vyshali.tradefillprocessor.dto.FillDetailsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TradeRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Save an execution report (fill) to the database.
     *
     * @return true if saved successfully, false if duplicate
     */
    public boolean saveExecution(ExecutionReportDTO fill) {
        // FIXED: Accessor names to match ExecutionReportDTO record definition:
        // - execID() -> execId()
        // - orderID() -> orderId()
        // - symbol() -> ticker()
        int rows = jdbcTemplate.update(TradeSql.INSERT_FILL, fill.execId(),   // FIXED: was fill.execID()
                fill.orderId(),  // FIXED: was fill.orderID()
                fill.ticker(),   // FIXED: was fill.symbol()
                fill.side(), fill.lastQty(), fill.lastPx());

        if (rows == 0) {
            log.warn("Duplicate Fill detected and ignored: ExecID={}", fill.execId());
            return false;
        }

        log.info("Persisted Fill: {}", fill.execId());
        return true;
    }

    /**
     * Get all fills for a specific order.
     * Called by TradeViewController when user expands an order row.
     */
    public List<FillDetailsDTO> getFillsForOrder(String orderId) {
        return jdbcTemplate.query("""
                SELECT fill_id, fill_qty, fill_price, trade_date, 'EXCHANGE' as venue
                FROM Trade_Fills
                WHERE order_id = ?
                ORDER BY trade_date ASC
                """, (rs, rowNum) -> new FillDetailsDTO(rs.getString("fill_id"), rs.getBigDecimal("fill_qty"), rs.getBigDecimal("fill_price"), rs.getTimestamp("trade_date").toLocalDateTime(), rs.getString("venue")), orderId);
    }

    /**
     * Mark a fill as processed.
     */
    public void markProcessed(String execId) {
        jdbcTemplate.update("""
                UPDATE Trade_Fills SET processed = TRUE
                WHERE fill_id = ?
                """, execId);
    }

    /**
     * Get unprocessed fills (for retry/recovery).
     */
    public List<ExecutionReportDTO> getUnprocessedFills() {
        return jdbcTemplate.query("""
                SELECT fill_id, order_id, symbol, side, fill_qty, fill_price
                FROM Trade_Fills
                WHERE processed = FALSE
                ORDER BY trade_date ASC
                LIMIT 100
                """, (rs, rowNum) -> new ExecutionReportDTO(rs.getString("order_id"), rs.getString("fill_id"), null,  // accountId not stored in Trade_Fills
                rs.getString("symbol"), rs.getString("side"), rs.getBigDecimal("fill_qty"), rs.getBigDecimal("fill_price"), "UNPROCESSED", null   // venue
        ));
    }

    /**
     * Count fills for an order.
     */
    public int countFillsForOrder(String orderId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM Trade_Fills WHERE order_id = ?
                """, Integer.class, orderId);
        return count != null ? count : 0;
    }
}