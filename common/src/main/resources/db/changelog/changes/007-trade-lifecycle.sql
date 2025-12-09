-- =====================================================
-- CRITICAL FIX #3: Trade Lifecycle Tracking Tables
-- Issue #2: "No tracking for trades executed from FX Analyzer"
-- =====================================================

-- Trade Lifecycle main table
CREATE TABLE IF NOT EXISTS Trade_Lifecycle (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id      VARCHAR(36) NOT NULL UNIQUE,

    account_id          INT NOT NULL,
    product_id          INT NOT NULL,
    product_ticker      VARCHAR(50),

    side                VARCHAR(10) NOT NULL,
    requested_quantity  DECIMAL(20, 8) NOT NULL,
    requested_price     DECIMAL(20, 8),
    currency            VARCHAR(3),

    status              VARCHAR(20) NOT NULL DEFAULT 'SENT',

    sent_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    acknowledged_at     TIMESTAMP WITH TIME ZONE,
    filled_at           TIMESTAMP WITH TIME ZONE,
    rejected_at         TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    filled_quantity     DECIMAL(20, 8),
    filled_price        DECIMAL(20, 8),
    fill_id             VARCHAR(100),

    external_ref_id     VARCHAR(100),
    reject_reason       VARCHAR(500),

    source_system       VARCHAR(50) DEFAULT 'FX_ANALYZER',
    destination_system  VARCHAR(50) DEFAULT 'FX_MATRIX',

    CONSTRAINT chk_status CHECK (status IN (
        'SENT', 'ACKNOWLEDGED', 'FILLED', 'PARTIALLY_FILLED',
        'REJECTED', 'CANCELLED', 'ORPHANED'
    )),
    CONSTRAINT chk_side CHECK (side IN ('BUY', 'SELL'))
);

CREATE INDEX idx_trade_lifecycle_account ON Trade_Lifecycle(account_id);
CREATE INDEX idx_trade_lifecycle_status ON Trade_Lifecycle(status);
CREATE INDEX idx_trade_lifecycle_sent_at ON Trade_Lifecycle(sent_at);
CREATE INDEX idx_trade_lifecycle_correlation ON Trade_Lifecycle(correlation_id);
CREATE INDEX idx_trade_lifecycle_orphan_check ON Trade_Lifecycle(status, sent_at)
    WHERE status IN ('SENT', 'ACKNOWLEDGED');

-- Trade Lifecycle Events (audit trail)
CREATE TABLE IF NOT EXISTS Trade_Lifecycle_Events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id      VARCHAR(36) NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_message       VARCHAR(500),
    error_code          VARCHAR(50),
    event_timestamp     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_events_lifecycle
        FOREIGN KEY (correlation_id)
        REFERENCES Trade_Lifecycle(correlation_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_lifecycle_events_correlation ON Trade_Lifecycle_Events(correlation_id);
CREATE INDEX idx_lifecycle_events_timestamp ON Trade_Lifecycle_Events(event_timestamp);

-- Daily reconciliation view
CREATE OR REPLACE VIEW Trade_Reconciliation_Daily AS
SELECT
    DATE(sent_at) as trade_date,
    COUNT(*) as total_trades,
    SUM(CASE WHEN status = 'FILLED' THEN 1 ELSE 0 END) as filled,
    SUM(CASE WHEN status = 'PARTIALLY_FILLED' THEN 1 ELSE 0 END) as partially_filled,
    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
    SUM(CASE WHEN status = 'ORPHANED' THEN 1 ELSE 0 END) as orphaned,
    SUM(CASE WHEN status IN ('SENT', 'ACKNOWLEDGED') THEN 1 ELSE 0 END) as pending,
    ROUND(
        SUM(CASE WHEN status IN ('FILLED', 'PARTIALLY_FILLED') THEN 1 ELSE 0 END)::DECIMAL /
        NULLIF(COUNT(*), 0) * 100, 2
    ) as fill_rate_pct
FROM Trade_Lifecycle
GROUP BY DATE(sent_at)
ORDER BY trade_date DESC;

-- Pending trades view
CREATE OR REPLACE VIEW Pending_Trades AS
SELECT
    correlation_id,
    account_id,
    product_ticker,
    side,
    requested_quantity,
    requested_price,
    status,
    sent_at,
    EXTRACT(EPOCH FROM (NOW() - sent_at)) / 60 as minutes_pending
FROM Trade_Lifecycle
WHERE status IN ('SENT', 'ACKNOWLEDGED')
ORDER BY sent_at ASC;

-- Orphan trades alert view
CREATE OR REPLACE VIEW Orphan_Trades_Alert AS
SELECT
    correlation_id,
    account_id,
    product_ticker,
    side,
    requested_quantity,
    sent_at,
    EXTRACT(EPOCH FROM (NOW() - sent_at)) / 60 as minutes_since_sent
FROM Trade_Lifecycle
WHERE status = 'ORPHANED'
ORDER BY sent_at DESC;