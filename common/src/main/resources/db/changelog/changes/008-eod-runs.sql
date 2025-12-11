-- liquibase formatted sql
-- changeset arch:eod-runs-1

-- ============================================================
-- EOD_RUNS TABLE
-- Tracks EOD processing status per account per day
-- Referenced by: PositionLoader DataRepository
-- ============================================================
CREATE TABLE IF NOT EXISTS eod_runs (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, RUNNING, COMPLETED, FAILED
    position_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_eod_runs_status ON eod_runs(status);
CREATE INDEX IF NOT EXISTS idx_eod_runs_date ON eod_runs(business_date DESC);
CREATE INDEX IF NOT EXISTS idx_eod_runs_account_date ON eod_runs(account_id, business_date);

-- ============================================================
-- DLQ TABLE (Dead Letter Queue for failed messages)
-- Referenced by: KafkaListeners DLQ retry logic
-- ============================================================
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

CREATE INDEX IF NOT EXISTS idx_dlq_retry ON dlq(retry_count, created_at);