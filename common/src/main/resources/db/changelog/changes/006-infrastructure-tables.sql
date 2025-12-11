-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 006: Infrastructure Tables
-- Author: fxanalyzer
-- Description: Audit Logs, DLQ, Shedlock, Ops Requests, Hedge Valuations
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:006-audit-logs
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(100),
    actor           VARCHAR(100),
    payload         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_type ON audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at DESC);
-- rollback DROP TABLE audit_logs;

-- changeset fxanalyzer:006-dlq
CREATE TABLE IF NOT EXISTS dlq (
    id              BIGSERIAL PRIMARY KEY,
    topic           VARCHAR(100) NOT NULL,
    message_key     VARCHAR(100),
    payload         TEXT,
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    status          VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT NOW(),
    last_retry_at   TIMESTAMP,
    next_retry_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dlq_retry ON dlq(retry_count, created_at);
CREATE INDEX IF NOT EXISTS idx_dlq_topic ON dlq(topic);
CREATE INDEX IF NOT EXISTS idx_dlq_status ON dlq(status) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_dlq_next_retry ON dlq(next_retry_at) WHERE status = 'PENDING';
-- rollback DROP TABLE dlq;

-- changeset fxanalyzer:006-shedlock
CREATE TABLE IF NOT EXISTS shedlock (
    name            VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until      TIMESTAMP NOT NULL,
    locked_at       TIMESTAMP NOT NULL,
    locked_by       VARCHAR(255) NOT NULL
);
-- rollback DROP TABLE shedlock;

-- changeset fxanalyzer:006-ops-requests
CREATE TABLE IF NOT EXISTS ops_requests (
    request_id      VARCHAR(50) PRIMARY KEY,
    action_type     VARCHAR(50) NOT NULL,
    payload         TEXT,
    status          VARCHAR(20) DEFAULT 'PENDING',
    requested_by    VARCHAR(50) NOT NULL,
    approved_by     VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ops_requests_status ON ops_requests(status);
CREATE INDEX IF NOT EXISTS idx_ops_requests_type ON ops_requests(action_type);
-- rollback DROP TABLE ops_requests;

-- changeset fxanalyzer:006-hedge-valuations
CREATE TABLE IF NOT EXISTS hedge_valuations (
    account_id      INT NOT NULL,
    business_date   DATE NOT NULL,
    valuation       DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);
CREATE INDEX IF NOT EXISTS idx_hedge_val_date ON hedge_valuations(business_date);
-- rollback DROP TABLE hedge_valuations;
