-- liquibase formatted sql
-- changeset arch:ops-schema-1

CREATE TABLE Ops_Requests (
    request_id VARCHAR(50) PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    requested_by VARCHAR(50) NOT NULL,
    approved_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_ops_requests_status ON Ops_Requests(status);

CREATE TABLE Trade_Fills (
    fill_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    fill_qty DECIMAL(18, 6) NOT NULL,
    fill_price DECIMAL(18, 6) NOT NULL,
    trade_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);