package com.vyshali.positionloader.service;

/*
 * 12/09/2025 - 3:45 PM
 * @author Vyshali Prabananth Lal
 */

/*
 * CRITICAL FIX #3: Trade Lifecycle Tracking
 *
 * Issue #2: "For trades executed from FX Analyzer – there is no tracking –
 *            we don't know what is executed and what is sent to trade"
 *
 * Solution: Complete trade lifecycle tracking with:
 * - Correlation IDs for each trade
 * - Status tracking (SENT → ACKNOWLEDGED → FILLED/REJECTED)
 * - Orphan detection for trades without response
 * - Reconciliation reports
 *
 * @author Vyshali Prabananth Lal
 */

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class TradeTrackingService {

    private static final Logger log = LoggerFactory.getLogger(TradeTrackingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter tradesSentCounter;
    private final Counter tradesFilledCounter;
    private final Counter tradesRejectedCounter;
    private final Counter orphanTradesCounter;

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_ORPHANED = "ORPHANED";

    public TradeTrackingService(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;

        this.tradesSentCounter = Counter.builder("trade.lifecycle.sent").description("Trades sent to FX Matrix").register(meterRegistry);
        this.tradesFilledCounter = Counter.builder("trade.lifecycle.filled").description("Trades successfully filled").register(meterRegistry);
        this.tradesRejectedCounter = Counter.builder("trade.lifecycle.rejected").description("Trades rejected by FX Matrix").register(meterRegistry);
        this.orphanTradesCounter = Counter.builder("trade.lifecycle.orphaned").description("Orphan trades detected").register(meterRegistry);
    }

    /**
     * Record a trade being sent to FX Matrix
     * Called BEFORE sending to FX Matrix
     */
    @Transactional
    public String recordTradeSent(TradeRequest request) {
        String correlationId = UUID.randomUUID().toString();

        String sql = """
                INSERT INTO Trade_Lifecycle (
                    correlation_id, account_id, product_id, product_ticker,
                    side, requested_quantity, requested_price, currency,
                    status, sent_at, source_system, destination_system
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, correlationId, request.accountId(), request.productId(), request.productTicker(), request.side(), request.quantity(), request.price(), request.currency(), STATUS_SENT, Timestamp.from(Instant.now()), "FX_ANALYZER", "FX_MATRIX");

        recordEvent(correlationId, STATUS_SENT, "Trade sent to FX Matrix", null);
        tradesSentCounter.increment();
        log.info("Trade SENT: correlationId={}, account={}, product={}, qty={}", correlationId, request.accountId(), request.productTicker(), request.quantity());

        return correlationId;
    }

    @Transactional
    public void recordTradeAcknowledged(String correlationId, String externalRefId) {
        String sql = """
                UPDATE Trade_Lifecycle 
                SET status = ?, external_ref_id = ?, acknowledged_at = ?, updated_at = ?
                WHERE correlation_id = ?
                """;

        int updated = jdbcTemplate.update(sql, STATUS_ACKNOWLEDGED, externalRefId, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), correlationId);

        if (updated > 0) {
            recordEvent(correlationId, STATUS_ACKNOWLEDGED, "Acknowledged by FX Matrix, ref=" + externalRefId, null);
            log.info("Trade ACKNOWLEDGED: correlationId={}, externalRef={}", correlationId, externalRefId);
        } else {
            log.warn("Trade not found for acknowledgment: correlationId={}", correlationId);
        }
    }

    @Transactional
    public void recordTradeFill(String correlationId, TradeFillResponse fill) {
        String status = fill.filledQuantity().compareTo(fill.requestedQuantity()) >= 0 ? STATUS_FILLED : STATUS_PARTIALLY_FILLED;

        String sql = """
                UPDATE Trade_Lifecycle 
                SET status = ?, 
                    filled_quantity = ?, 
                    filled_price = ?, 
                    fill_id = ?,
                    filled_at = ?, 
                    updated_at = ?
                WHERE correlation_id = ?
                """;

        int updated = jdbcTemplate.update(sql, status, fill.filledQuantity(), fill.filledPrice(), fill.fillId(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), correlationId);

        if (updated > 0) {
            recordEvent(correlationId, status, String.format("Filled: qty=%s, price=%s", fill.filledQuantity(), fill.filledPrice()), null);
            tradesFilledCounter.increment();
            log.info("Trade FILLED: correlationId={}, qty={}, price={}", correlationId, fill.filledQuantity(), fill.filledPrice());
        } else {
            log.warn("Trade not found for fill: correlationId={}", correlationId);
        }
    }

    @Transactional
    public void recordTradeRejected(String correlationId, String rejectReason, String rejectCode) {
        String sql = """
                UPDATE Trade_Lifecycle 
                SET status = ?, 
                    reject_reason = ?,
                    rejected_at = ?, 
                    updated_at = ?
                WHERE correlation_id = ?
                """;

        int updated = jdbcTemplate.update(sql, STATUS_REJECTED, rejectReason, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), correlationId);

        if (updated > 0) {
            recordEvent(correlationId, STATUS_REJECTED, rejectReason, rejectCode);
            tradesRejectedCounter.increment();
            log.warn("Trade REJECTED: correlationId={}, reason={}", correlationId, rejectReason);
        } else {
            log.warn("Trade not found for rejection: correlationId={}", correlationId);
        }
    }

    @Transactional
    public void recordTradeCancelled(String correlationId, String cancelReason) {
        String sql = """
                UPDATE Trade_Lifecycle 
                SET status = ?, 
                    reject_reason = ?,
                    updated_at = ?
                WHERE correlation_id = ?
                """;

        jdbcTemplate.update(sql, STATUS_CANCELLED, cancelReason, Timestamp.from(Instant.now()), correlationId);

        recordEvent(correlationId, STATUS_CANCELLED, cancelReason, null);
        log.info("Trade CANCELLED: correlationId={}, reason={}", correlationId, cancelReason);
    }

    private void recordEvent(String correlationId, String status, String message, String errorCode) {
        String sql = """
                INSERT INTO Trade_Lifecycle_Events (
                    correlation_id, event_type, event_message, error_code, event_timestamp
                ) VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, correlationId, status, message, errorCode, Timestamp.from(Instant.now()));
    }

    /**
     * CRITICAL: Detect orphan trades - trades sent but no response received
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void detectOrphanTrades() {
        Instant threshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        String sql = """
                SELECT correlation_id, account_id, product_ticker, requested_quantity, sent_at
                FROM Trade_Lifecycle
                WHERE status IN (?, ?)
                AND sent_at < ?
                """;

        List<OrphanTrade> orphans = jdbcTemplate.query(sql, (rs, rowNum) -> new OrphanTrade(rs.getString("correlation_id"), rs.getInt("account_id"), rs.getString("product_ticker"), rs.getBigDecimal("requested_quantity"), rs.getTimestamp("sent_at").toInstant()), STATUS_SENT, STATUS_ACKNOWLEDGED, Timestamp.from(threshold));

        if (!orphans.isEmpty()) {
            log.error("ALERT: {} orphan trades detected!", orphans.size());

            for (OrphanTrade orphan : orphans) {
                log.error("  - correlationId={}, account={}, product={}, qty={}, sentAt={}", orphan.correlationId(), orphan.accountId(), orphan.productTicker(), orphan.quantity(), orphan.sentAt());

                jdbcTemplate.update("""
                        UPDATE Trade_Lifecycle 
                        SET status = ?, updated_at = ?
                        WHERE correlation_id = ?
                        """, STATUS_ORPHANED, Timestamp.from(Instant.now()), orphan.correlationId());

                recordEvent(orphan.correlationId(), STATUS_ORPHANED, "No response received after 5 minutes", "TIMEOUT");

                orphanTradesCounter.increment();
            }
        }
    }

    public TradeLifecycle getTrade(String correlationId) {
        String sql = "SELECT * FROM Trade_Lifecycle WHERE correlation_id = ?";
        List<TradeLifecycle> trades = jdbcTemplate.query(sql, tradeRowMapper, correlationId);
        return trades.isEmpty() ? null : trades.get(0);
    }

    public List<TradeLifecycle> getTradesForAccount(Integer accountId, LocalDate date) {
        String sql = """
                SELECT * FROM Trade_Lifecycle 
                WHERE account_id = ? 
                AND DATE(sent_at) = ?
                ORDER BY sent_at DESC
                """;
        return jdbcTemplate.query(sql, tradeRowMapper, accountId, date);
    }

    public List<TradeLifecycle> getOrphanedTrades() {
        String sql = "SELECT * FROM Trade_Lifecycle WHERE status = ? ORDER BY sent_at DESC";
        return jdbcTemplate.query(sql, tradeRowMapper, STATUS_ORPHANED);
    }

    public List<TradeLifecycle> getPendingTrades() {
        String sql = """
                SELECT * FROM Trade_Lifecycle 
                WHERE status IN (?, ?)
                ORDER BY sent_at ASC
                """;
        return jdbcTemplate.query(sql, tradeRowMapper, STATUS_SENT, STATUS_ACKNOWLEDGED);
    }

    public List<TradeEvent> getTradeEvents(String correlationId) {
        String sql = """
                SELECT * FROM Trade_Lifecycle_Events 
                WHERE correlation_id = ?
                ORDER BY event_timestamp ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TradeEvent(rs.getString("correlation_id"), rs.getString("event_type"), rs.getString("event_message"), rs.getString("error_code"), rs.getTimestamp("event_timestamp").toInstant()), correlationId);
    }

    public ReconciliationSummary getReconciliationSummary(LocalDate date) {
        String sql = """
                SELECT 
                    COUNT(*) as total,
                    SUM(CASE WHEN status = 'FILLED' THEN 1 ELSE 0 END) as filled,
                    SUM(CASE WHEN status = 'PARTIALLY_FILLED' THEN 1 ELSE 0 END) as partial,
                    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
                    SUM(CASE WHEN status = 'ORPHANED' THEN 1 ELSE 0 END) as orphaned,
                    SUM(CASE WHEN status IN ('SENT', 'ACKNOWLEDGED') THEN 1 ELSE 0 END) as pending
                FROM Trade_Lifecycle
                WHERE DATE(sent_at) = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ReconciliationSummary(date, rs.getInt("total"), rs.getInt("filled"), rs.getInt("partial"), rs.getInt("rejected"), rs.getInt("orphaned"), rs.getInt("pending")), date);
    }

    private final RowMapper<TradeLifecycle> tradeRowMapper = (rs, rowNum) -> new TradeLifecycle(rs.getString("correlation_id"), rs.getInt("account_id"), rs.getInt("product_id"), rs.getString("product_ticker"), rs.getString("side"), rs.getBigDecimal("requested_quantity"), rs.getBigDecimal("requested_price"), rs.getString("currency"), rs.getString("status"), getInstant(rs, "sent_at"), getInstant(rs, "acknowledged_at"), getInstant(rs, "filled_at"), getInstant(rs, "rejected_at"), rs.getBigDecimal("filled_quantity"), rs.getBigDecimal("filled_price"), rs.getString("fill_id"), rs.getString("external_ref_id"), rs.getString("reject_reason"), rs.getString("source_system"), rs.getString("destination_system"));

    private Instant getInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    // DTOs
    public record TradeRequest(Integer accountId, Integer productId, String productTicker, String side,
                               BigDecimal quantity, BigDecimal price, String currency) {
    }

    public record TradeFillResponse(String fillId, BigDecimal requestedQuantity, BigDecimal filledQuantity,
                                    BigDecimal filledPrice) {
    }

    public record TradeLifecycle(String correlationId, Integer accountId, Integer productId, String productTicker,
                                 String side, BigDecimal requestedQuantity, BigDecimal requestedPrice, String currency,
                                 String status, Instant sentAt, Instant acknowledgedAt, Instant filledAt,
                                 Instant rejectedAt, BigDecimal filledQuantity, BigDecimal filledPrice, String fillId,
                                 String externalRefId, String rejectReason, String sourceSystem,
                                 String destinationSystem) {
        public boolean isPending() {
            return STATUS_SENT.equals(status) || STATUS_ACKNOWLEDGED.equals(status);
        }

        public boolean isComplete() {
            return STATUS_FILLED.equals(status) || STATUS_REJECTED.equals(status);
        }
    }

    public record TradeEvent(String correlationId, String eventType, String message, String errorCode,
                             Instant timestamp) {
    }

    public record OrphanTrade(String correlationId, Integer accountId, String productTicker, BigDecimal quantity,
                              Instant sentAt) {
    }

    public record ReconciliationSummary(LocalDate date, int total, int filled, int partiallyFilled, int rejected,
                                        int orphaned, int pending) {
        public double fillRate() {
            return total > 0 ? (double) (filled + partiallyFilled) / total * 100 : 0;
        }

        public boolean hasIssues() {
            return orphaned > 0 || pending > 0;
        }
    }
}
