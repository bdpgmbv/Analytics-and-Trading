-- liquibase formatted sql
-- changeset positionloader:4
CREATE TABLE Ops_Audit_Log (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL, -- 'TRIGGER_EOD', 'TRIGGER_INTRA'
    target_id VARCHAR(50),            -- Account ID
    triggered_by VARCHAR(100),        -- User from OAuth Token
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20)
);