-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 005: EOD and Batch Processing Tables
-- Author: fxanalyzer
-- Description: EOD runs, batch switching, daily status
-- CRITICAL: account_batches defined ONCE with ALL Phase 2 columns
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:005-account-batches
-- NOTE: This is the SINGLE source of truth for account_batches
-- Includes all Phase 2 columns: business_date, error_message, activated_at, archived_at
CREATE TABLE IF NOT EXISTS account_batches (
    id              SERIAL,
    account_id      INT NOT NULL,
    batch_id        INT NOT NULL,
    business_date   DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'STAGING',
    position_count  INT DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at    TIMESTAMP,
    archived_at     TIMESTAMP,
    PRIMARY KEY (account_id, batch_id),
    CONSTRAINT fk_account_batches_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);
COMMENT ON TABLE account_batches IS 'Phase 2: Batch tracking for blue/green position deployment';
COMMENT ON COLUMN account_batches.status IS 'STAGING=loading, ACTIVE=current, ARCHIVED=previous, FAILED=error, ROLLED_BACK=reverted';
COMMENT ON COLUMN account_batches.business_date IS 'Phase 2: Business date for this batch';
COMMENT ON COLUMN account_batches.error_message IS 'Phase 2: Error details if batch failed';
COMMENT ON COLUMN account_batches.activated_at IS 'Phase 2: When batch became ACTIVE';
COMMENT ON COLUMN account_batches.archived_at IS 'Phase 2: When batch was archived';

CREATE INDEX IF NOT EXISTS idx_account_batches_status 
    ON account_batches(account_id, business_date, status);
CREATE INDEX IF NOT EXISTS idx_account_batches_active 
    ON account_batches(account_id, status) WHERE status = 'ACTIVE';
-- rollback DROP TABLE account_batches;

-- changeset fxanalyzer:005-eod-runs
CREATE TABLE IF NOT EXISTS eod_runs (
    account_id      INT NOT NULL,
    business_date   DATE NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    position_count  INT DEFAULT 0,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);
CREATE INDEX IF NOT EXISTS idx_eod_runs_status ON eod_runs(status);
CREATE INDEX IF NOT EXISTS idx_eod_runs_date ON eod_runs(business_date DESC);
-- rollback DROP TABLE eod_runs;

-- changeset fxanalyzer:005-eod-daily-status
CREATE TABLE IF NOT EXISTS eod_daily_status (
    account_id      INT NOT NULL,
    client_id       INT NOT NULL,
    business_date   DATE NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);
CREATE INDEX IF NOT EXISTS idx_eod_status_client_date ON eod_daily_status(client_id, business_date);
CREATE INDEX IF NOT EXISTS idx_eod_status_status ON eod_daily_status(status);
-- rollback DROP TABLE eod_daily_status;
