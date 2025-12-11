-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 008: Database Views
-- Author: fxanalyzer
-- Description: Reporting and operational views
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:008-v-active-positions
CREATE OR REPLACE VIEW v_active_positions AS
SELECT 
    p.position_id,
    p.account_id,
    a.account_number,
    a.client_id,
    c.client_name,
    p.product_id,
    pr.ticker,
    pr.asset_class,
    p.business_date,
    p.quantity,
    p.price,
    p.currency,
    p.market_value_local,
    p.market_value_base,
    p.batch_id,
    p.source,
    p.position_type,
    p.is_excluded,
    p.created_at
FROM positions p
JOIN accounts a ON p.account_id = a.account_id
JOIN clients c ON a.client_id = c.client_id
JOIN products pr ON p.product_id = pr.product_id
WHERE p.is_excluded = FALSE
  AND p.system_to = '9999-12-31 23:59:59';
-- rollback DROP VIEW v_active_positions;

-- changeset fxanalyzer:008-v-eod-summary
CREATE OR REPLACE VIEW v_eod_summary AS
SELECT 
    er.account_id,
    a.account_number,
    a.client_id,
    c.client_name,
    er.business_date,
    er.status,
    er.position_count,
    er.started_at,
    er.completed_at,
    EXTRACT(EPOCH FROM (er.completed_at - er.started_at)) AS duration_seconds,
    er.error_message
FROM eod_runs er
JOIN accounts a ON er.account_id = a.account_id
JOIN clients c ON a.client_id = c.client_id;
-- rollback DROP VIEW v_eod_summary;

-- changeset fxanalyzer:008-v-daily-position-counts
CREATE OR REPLACE VIEW v_daily_position_counts AS
SELECT 
    p.account_id,
    a.account_number,
    a.client_id,
    p.business_date,
    COUNT(*) AS position_count,
    SUM(p.quantity) AS total_quantity,
    SUM(p.market_value_base) AS total_market_value_base,
    COUNT(DISTINCT p.product_id) AS unique_products
FROM positions p
JOIN accounts a ON p.account_id = a.account_id
WHERE p.is_excluded = FALSE
GROUP BY p.account_id, a.account_number, a.client_id, p.business_date;
-- rollback DROP VIEW v_daily_position_counts;

-- changeset fxanalyzer:008-v-batch-status
CREATE OR REPLACE VIEW v_batch_status AS
SELECT 
    ab.account_id,
    a.account_number,
    a.client_id,
    ab.batch_id,
    ab.business_date,
    ab.status,
    ab.position_count,
    ab.created_at,
    ab.activated_at,
    ab.archived_at,
    ab.error_message
FROM account_batches ab
JOIN accounts a ON ab.account_id = a.account_id;
-- rollback DROP VIEW v_batch_status;

-- changeset fxanalyzer:008-v-dlq-summary
CREATE OR REPLACE VIEW v_dlq_summary AS
SELECT 
    topic,
    status,
    COUNT(*) AS message_count,
    MIN(created_at) AS oldest_message,
    MAX(created_at) AS newest_message,
    AVG(retry_count) AS avg_retries
FROM dlq
GROUP BY topic, status;
-- rollback DROP VIEW v_dlq_summary;

-- changeset fxanalyzer:008-v-trade-reconciliation
CREATE OR REPLACE VIEW v_trade_reconciliation AS
SELECT 
    tl.correlation_id,
    tl.account_id,
    a.account_number,
    tl.product_ticker,
    tl.side,
    tl.requested_quantity,
    tl.filled_quantity,
    tl.requested_price,
    tl.filled_price,
    tl.status,
    tl.sent_at,
    tl.filled_at,
    EXTRACT(EPOCH FROM (tl.filled_at - tl.sent_at)) AS fill_latency_seconds
FROM trade_lifecycle tl
JOIN accounts a ON tl.account_id = a.account_id;
-- rollback DROP VIEW v_trade_reconciliation;

-- changeset fxanalyzer:008-v-pending-trades
CREATE OR REPLACE VIEW v_pending_trades AS
SELECT 
    tl.correlation_id,
    tl.account_id,
    a.account_number,
    tl.product_ticker,
    tl.side,
    tl.requested_quantity,
    tl.status,
    tl.sent_at,
    NOW() - tl.sent_at AS age
FROM trade_lifecycle tl
JOIN accounts a ON tl.account_id = a.account_id
WHERE tl.status IN ('SENT', 'ACKNOWLEDGED', 'PARTIALLY_FILLED');
-- rollback DROP VIEW v_pending_trades;
