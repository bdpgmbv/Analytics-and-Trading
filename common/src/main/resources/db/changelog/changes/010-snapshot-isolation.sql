-- liquibase formatted sql
-- changeset arch:snapshot-isolation-1

-- 1. Create Batch Tracking Table
-- This table controls which version of the data is visible to the application.
CREATE TABLE Account_Batches (
    account_id INT NOT NULL,
    batch_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'STAGING', -- 'ACTIVE', 'STAGING', 'ARCHIVED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, batch_id)
);

-- 2. Add batch_id to Positions table
-- Defaulting to 1 ensures existing data remains valid until the first migration.
ALTER TABLE Positions ADD COLUMN batch_id INT DEFAULT 1;

-- 3. Initialize default batch (1) as ACTIVE for all existing accounts
INSERT INTO Account_Batches (account_id, batch_id, status)
SELECT DISTINCT account_id, 1, 'ACTIVE' FROM Positions;

-- 4. Create Indexes for Performance
-- Critical: All queries will now filter by (account_id, batch_id)
CREATE INDEX idx_pos_batch_status ON Positions(account_id, batch_id);
CREATE INDEX idx_batch_status ON Account_Batches(account_id, status);