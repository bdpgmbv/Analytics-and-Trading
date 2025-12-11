-- ═══════════════════════════════════════════════════════════════════════════
-- POSITION LOADER - COMPLETE TEST SCHEMA
-- Includes all Phase 1-4 tables
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- CORE REFERENCE TABLES
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100),
    base_currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS Accounts (
    account_id INT PRIMARY KEY,
    client_id INT,
    client_name VARCHAR(100),
    fund_id INT,
    fund_name VARCHAR(100),
    base_currency VARCHAR(3) DEFAULT 'USD',
    account_number VARCHAR(50) UNIQUE
);

CREATE TABLE IF NOT EXISTS Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20),
    asset_class VARCHAR(50)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- POSITIONS (with batch_id for Phase 2)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS positions (
    position_id BIGSERIAL PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    batch_id INT DEFAULT 1,
    quantity DECIMAL(18,6) DEFAULT 0,
    price DECIMAL(18,6) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD',
    source VARCHAR(20) DEFAULT 'MSPM',
    business_date DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE (account_id, product_id, business_date)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- PHASE 2: BATCH SWITCHING
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS account_batches (
    account_id INT NOT NULL,
    batch_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'STAGING',  -- STAGING, ACTIVE, ARCHIVED, ROLLED_BACK, FAILED
    position_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    activated_at TIMESTAMP,
    archived_at TIMESTAMP,
    PRIMARY KEY (account_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_batches_status ON account_batches(account_id, business_date, status);

-- ═══════════════════════════════════════════════════════════════════════════
-- EOD STATUS TABLES
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS eod_runs (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, RUNNING, COMPLETED, FAILED
    position_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    PRIMARY KEY (account_id, business_date)
);

CREATE TABLE IF NOT EXISTS Eod_Daily_Status (
    account_id INT NOT NULL,
    client_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- AUDIT & DLQ
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS Audit_Logs (
    audit_id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50),
    entity_id VARCHAR(100),
    actor VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dlq (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100),
    payload TEXT,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    last_retry_at TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════════════════════
-- PHASE 4 #16: DUPLICATE DETECTION
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS snapshot_hashes (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    content_hash VARCHAR(64) NOT NULL,  -- MD5 hex = 32 chars
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (account_id, business_date)
);

CREATE INDEX IF NOT EXISTS idx_snapshot_hashes_lookup
    ON snapshot_hashes(account_id, content_hash, business_date);

-- ═══════════════════════════════════════════════════════════════════════════
-- PHASE 4 #17: HOLIDAY CALENDAR
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS holidays (
    id SERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(100) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (holiday_date, country)
);

CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(holiday_date);

-- Sample holidays for testing
INSERT INTO holidays (holiday_date, holiday_name, country) VALUES
    ('2025-01-01', 'New Years Day', 'US'),
    ('2025-07-04', 'Independence Day', 'US'),
    ('2025-12-25', 'Christmas Day', 'US')
ON CONFLICT DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- PHASE 4 #22: DATA ARCHIVAL
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS positions_archive (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity DECIMAL(18,6),
    price DECIMAL(18,6),
    currency VARCHAR(3),
    business_date DATE NOT NULL,
    batch_id INT,
    source VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    archived_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_positions_archive_date
    ON positions_archive(business_date);
CREATE INDEX IF NOT EXISTS idx_positions_archive_account
    ON positions_archive(account_id, business_date);

-- ═══════════════════════════════════════════════════════════════════════════
-- HEDGE SERVICE TABLE
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS hedge_valuations (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    valuation DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

-- ═══════════════════════════════════════════════════════════════════════════
-- PERFORMANCE INDEXES
-- ═══════════════════════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS idx_positions_account_date ON positions(account_id, business_date);
CREATE INDEX IF NOT EXISTS idx_positions_batch ON positions(account_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_eod_status_client_date ON eod_daily_status(client_id, business_date, status);
CREATE INDEX IF NOT EXISTS idx_eod_runs_account_date ON eod_runs(account_id, business_date);
CREATE INDEX IF NOT EXISTS idx_audit_entity_date ON audit_logs(entity_id, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════════
-- TEST DATA
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO Clients (client_id, client_name) VALUES (100, 'Test Client') ON CONFLICT DO NOTHING;
INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (200, 100, 'Test Fund', 'USD') ON CONFLICT DO NOTHING;
INSERT INTO Accounts (account_id, client_id, fund_id, base_currency, account_number) VALUES (1001, 100, 200, 'USD', 'ACC-1001') ON CONFLICT DO NOTHING;
INSERT INTO Products (product_id, ticker, asset_class) VALUES (1, 'AAPL', 'EQUITY') ON CONFLICT DO NOTHING;
INSERT INTO Products (product_id, ticker, asset_class) VALUES (2, 'GOOG', 'EQUITY') ON CONFLICT DO NOTHING;
INSERT INTO Products (product_id, ticker, asset_class) VALUES (3, 'MSFT', 'EQUITY') ON CONFLICT DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Active positions view (joins with account_batches)
CREATE OR REPLACE VIEW v_active_positions AS
SELECT
    p.account_id,
    p.product_id,
    p.quantity,
    p.price,
    p.currency,
    p.business_date,
    p.batch_id,
    p.source,
    p.created_at
FROM positions p
JOIN account_batches b ON p.account_id = b.account_id AND p.batch_id = b.batch_id
WHERE b.status = 'ACTIVE';

-- EOD status summary view
CREATE OR REPLACE VIEW v_eod_summary AS
SELECT
    business_date,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed,
    COUNT(*) FILTER (WHERE status = 'RUNNING') as running,
    COUNT(*) as total
FROM eod_runs
WHERE business_date >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY business_date
ORDER BY business_date DESC;