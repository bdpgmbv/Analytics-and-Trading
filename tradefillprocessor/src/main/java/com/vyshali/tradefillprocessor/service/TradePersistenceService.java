package com.vyshali.tradefillprocessor.service;

import com.vyshali.tradefillprocessor.dto.OrderStateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * IMPROVED: Trade persistence with orphan marking support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradePersistenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void updateOrderSummary(OrderStateDTO state) {
        BigDecimal avgPx = state.totalFilledQty().compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : state.totalNotional().divide(state.totalFilledQty(), 6, RoundingMode.HALF_UP);

        String sql = """
            INSERT INTO client_orders (order_id, account_id, ticker, side, status, filled_qty, avg_price, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO UPDATE SET
                status = EXCLUDED.status,
                filled_qty = EXCLUDED.filled_qty,
                avg_price = EXCLUDED.avg_price,
                updated_at = CURRENT_TIMESTAMP
            """;

        jdbcTemplate.update(sql,
            state.orderId(),
            state.accountId(),
            state.ticker(),
            state.side(),
            state.status(),
            state.totalFilledQty(),
            avgPx
        );
        
        log.debug("Order summary updated: orderId={}, status={}", state.orderId(), state.status());
    }

    /**
     * Mark an order as orphaned in both client_orders and trade_lifecycle.
     */
    @Transactional
    public void markAsOrphaned(String orderId) {
        // Update client_orders
        int updated = jdbcTemplate.update("""
            UPDATE client_orders
            SET status = 'ORPHANED', updated_at = CURRENT_TIMESTAMP
            WHERE order_id = ?
              AND status NOT IN ('FILLED', 'REJECTED', 'CANCELLED', 'ORPHANED')
            """, orderId);

        // Update trade_lifecycle if exists
        jdbcTemplate.update("""
            UPDATE trade_lifecycle
            SET status = 'ORPHANED', updated_at = NOW()
            WHERE correlation_id = ?
              AND status NOT IN ('FILLED', 'REJECTED', 'CANCELLED', 'ORPHANED')
            """, orderId);

        if (updated > 0) {
            log.info("Order marked as orphaned: {}", orderId);
        }
    }

    /**
     * Get count of orphaned orders for monitoring.
     */
    public int getOrphanedOrderCount() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM client_orders WHERE status = 'ORPHANED'",
            Integer.class
        );
        return count != null ? count : 0;
    }
}
