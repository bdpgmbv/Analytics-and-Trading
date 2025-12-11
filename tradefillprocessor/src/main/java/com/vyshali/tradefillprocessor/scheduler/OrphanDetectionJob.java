package com.vyshali.tradefillprocessor.scheduler;

/*
 * 12/11/2025 - Orphan Detection Scheduled Job
 * @author Vyshali Prabananth Lal
 *
 * Detects orders that have been in SENT or ACKNOWLEDGED state too long
 * without receiving fills. Marks them as ORPHANED for investigation.
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OrphanDetectionJob {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.trade.orphan-threshold-minutes:30}")
    private int orphanThresholdMinutes;

    /**
     * Run orphan detection every 5 minutes.
     * Finds orders in SENT/ACKNOWLEDGED state older than threshold.
     */
    @Scheduled(fixedRateString = "${app.trade.orphan-check-interval-ms:300000}")
    public void detectOrphanedOrders() {
        log.info("Starting orphan detection scan...");

        try {
            // Find orders that haven't received fills in the threshold time
            String sql = """
                    SELECT order_id, account_id, ticker, side, status, created_at,
                           EXTRACT(EPOCH FROM (NOW() - created_at)) / 60 as minutes_pending
                    FROM client_orders
                    WHERE status IN ('NEW', 'PENDING_NEW', 'PARTIALLY_FILLED')
                      AND created_at < NOW() - INTERVAL '%d minutes'
                    ORDER BY created_at ASC
                    """.formatted(orphanThresholdMinutes);

            List<Map<String, Object>> orphans = jdbcTemplate.queryForList(sql);

            if (orphans.isEmpty()) {
                log.info("No orphaned orders detected");
                return;
            }

            log.warn("Found {} potential orphaned orders", orphans.size());

            for (Map<String, Object> orphan : orphans) {
                String orderId = (String) orphan.get("order_id");
                Number minutesPending = (Number) orphan.get("minutes_pending");

                log.warn("Orphaned order detected: OrderId={}, Status={}, PendingMinutes={}",
                        orderId, orphan.get("status"), minutesPending);

                // Mark as orphaned
                markAsOrphaned(orderId);
            }

            log.info("Orphan detection complete: {} orders marked as orphaned", orphans.size());

        } catch (Exception e) {
            log.error("Orphan detection failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Mark an order as orphaned in the database.
     */
    private void markAsOrphaned(String orderId) {
        String sql = """
                UPDATE client_orders
                SET status = 'ORPHANED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE order_id = ?
                  AND status NOT IN ('FILLED', 'REJECTED', 'CANCELLED', 'ORPHANED')
                """;

        int updated = jdbcTemplate.update(sql, orderId);

        if (updated > 0) {
            log.info("Order marked as orphaned: {}", orderId);

            // Also update trade_lifecycle if exists
            jdbcTemplate.update("""
                    UPDATE trade_lifecycle
                    SET status = 'ORPHANED',
                        updated_at = NOW()
                    WHERE correlation_id = ?
                      AND status NOT IN ('FILLED', 'REJECTED', 'CANCELLED', 'ORPHANED')
                    """, orderId);
        }
    }

    /**
     * Generate orphan report for monitoring.
     * Called on demand or by separate reporting job.
     */
    public Map<String, Object> getOrphanReport() {
        String sql = """
                SELECT 
                    COUNT(*) FILTER (WHERE status = 'ORPHANED') as orphaned_count,
                    COUNT(*) FILTER (WHERE status IN ('NEW', 'PENDING_NEW')) as pending_count,
                    COUNT(*) FILTER (WHERE status = 'PARTIALLY_FILLED') as partial_count,
                    MIN(created_at) FILTER (WHERE status IN ('NEW', 'PENDING_NEW', 'PARTIALLY_FILLED')) as oldest_pending
                FROM client_orders
                WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
                """;

        return jdbcTemplate.queryForMap(sql);
    }
}
