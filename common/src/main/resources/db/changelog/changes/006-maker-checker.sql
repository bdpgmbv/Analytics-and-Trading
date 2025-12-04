-- liquibase formatted sql
-- changeset ops:maker-checker-1

CREATE TABLE Ops_Requests (
    request_id VARCHAR(50) PRIMARY KEY, -- Unique ID (e.g., "req-123")
    action_type VARCHAR(50) NOT NULL,   -- What are they trying to do? (e.g., "TRIGGER_EOD")
    payload TEXT,                       -- Details (e.g., "{"accountId": 1001}")

    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED

    requested_by VARCHAR(50) NOT NULL,  -- Who started it? (The Maker)
    approved_by VARCHAR(50),            -- Who finished it? (The Checker)

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);