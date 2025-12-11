-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 007: Phase 4 Features
-- Author: fxanalyzer
-- Description: Holidays, Snapshot Hashes (duplicate detection), Position Archive, System Alerts
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:007-holidays
-- Phase 4 Enhancement #20: Holiday calendar for late EOD processing
CREATE TABLE IF NOT EXISTS holidays (
    id              SERIAL PRIMARY KEY,
    holiday_date    DATE NOT NULL,
    country_code    VARCHAR(3) NOT NULL DEFAULT 'US',
    holiday_name    VARCHAR(100),
    is_half_day     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_holidays_date_country ON holidays(holiday_date, country_code);
CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(holiday_date);
COMMENT ON TABLE holidays IS 'Phase 4 #20: Holiday calendar for late EOD processing';
-- rollback DROP TABLE holidays;

-- changeset fxanalyzer:007-snapshot-hashes
-- Phase 4 Enhancement #19: Duplicate position detection via content hashing
CREATE TABLE IF NOT EXISTS snapshot_hashes (
    id              SERIAL PRIMARY KEY,
    account_id      INT NOT NULL,
    business_date   DATE NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,
    position_count  INT NOT NULL,
    total_quantity  DECIMAL(20, 6),
    total_market_value DECIMAL(20, 6),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshot_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_snapshot_account_date ON snapshot_hashes(account_id, business_date);
CREATE INDEX IF NOT EXISTS idx_snapshot_hash ON snapshot_hashes(content_hash);
COMMENT ON TABLE snapshot_hashes IS 'Phase 4 #19: Content hashes for duplicate detection';
-- rollback DROP TABLE snapshot_hashes;

-- changeset fxanalyzer:007-positions-archive
-- Phase 4 Enhancement #18: Historical position archival
CREATE TABLE IF NOT EXISTS positions_archive (
    archive_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_position_id BIGINT NOT NULL,
    account_id          INT NOT NULL,
    product_id          INT NOT NULL,
    business_date       DATE NOT NULL,
    quantity            DECIMAL(18, 6) NOT NULL,
    price               DECIMAL(18, 6),
    currency            VARCHAR(3),
    market_value_local  DECIMAL(18, 6),
    market_value_base   DECIMAL(18, 6),
    batch_id            INT,
    source              VARCHAR(20),
    archived_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archive_reason      VARCHAR(50) DEFAULT 'RETENTION_POLICY'
);
CREATE INDEX IF NOT EXISTS idx_archive_account_date ON positions_archive(account_id, business_date);
CREATE INDEX IF NOT EXISTS idx_archive_archived_at ON positions_archive(archived_at);
COMMENT ON TABLE positions_archive IS 'Phase 4 #18: Archived positions for historical retention';
-- rollback DROP TABLE positions_archive;

-- changeset fxanalyzer:007-system-alerts
-- Phase 4 Enhancement #22: System alerting for DLQ threshold breaches
CREATE TABLE IF NOT EXISTS system_alerts (
    id              SERIAL PRIMARY KEY,
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    message         TEXT NOT NULL,
    source          VARCHAR(100),
    acknowledged    BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(50),
    acknowledged_at TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alerts_type ON system_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON system_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_unacked ON system_alerts(acknowledged) WHERE acknowledged = FALSE;
COMMENT ON TABLE system_alerts IS 'Phase 4 #22: System alerts for monitoring';
-- rollback DROP TABLE system_alerts;
