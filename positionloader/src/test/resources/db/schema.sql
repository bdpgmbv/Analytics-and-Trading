-- Test Schema for Integration Tests
-- This is a simplified schema for Testcontainers PostgreSQL

-- Batch Control
CREATE TABLE IF NOT EXISTS batch_control (
    account_id INT PRIMARY KEY,
    active_batch_id INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Clients
CREATE TABLE IF NOT EXISTS Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Funds
CREATE TABLE IF NOT EXISTS Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Accounts
CREATE TABLE IF NOT EXISTS Accounts (
    account_id INT PRIMARY KEY,
    client_id INT,
    client_name VARCHAR(100),
    fund_id INT,
    fund_name VARCHAR(100),
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    account_number VARCHAR(50) UNIQUE,
    account_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Products
CREATE TABLE IF NOT EXISTS Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20),
    asset_class VARCHAR(50),
    issue_currency VARCHAR(3),
    settlement_currency VARCHAR(3),
    identifier_type VARCHAR(20) DEFAULT 'TICKER',
    identifier_value VARCHAR(50),
    risk_region VARCHAR(50),
    description VARCHAR(255)
);

-- Positions
CREATE TABLE IF NOT EXISTS Positions (
    position_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    batch_id INT NOT NULL DEFAULT 1,
    quantity DECIMAL(18, 6) NOT NULL DEFAULT 0,
    avg_cost_price DECIMAL(18, 6) DEFAULT 0,
    cost_local DECIMAL(18, 6) DEFAULT 0,
    price DECIMAL(18, 6) DEFAULT 0,
    market_value_base DECIMAL(18, 6) DEFAULT 0,
    source_system VARCHAR(20) DEFAULT 'MSPM',
    position_type VARCHAR(20) DEFAULT 'PHYSICAL',
    external_ref_id VARCHAR(100),
    business_date DATE,
    system_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    system_to TIMESTAMP DEFAULT '9999-12-31 23:59:59',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, product_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_positions_account ON Positions(account_id);
CREATE INDEX IF NOT EXISTS idx_positions_external_ref ON Positions(external_ref_id);

-- Account Batches
CREATE TABLE IF NOT EXISTS Account_Batches (
    account_id INT NOT NULL,
    batch_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'STAGING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, batch_id)
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS Audit_Logs (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100),
    actor VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- EOD Daily Status
CREATE TABLE IF NOT EXISTS Eod_Daily_Status (
    account_id INT NOT NULL,
    client_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

-- Ops Requests (for maker-checker)
CREATE TABLE IF NOT EXISTS Ops_Requests (
    request_id VARCHAR(50) PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    payload TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    requested_by VARCHAR(50) NOT NULL,
    approved_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Trade Fills
CREATE TABLE IF NOT EXISTS Trade_Fills (
    fill_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    fill_qty DECIMAL(18, 6) NOT NULL,
    fill_price DECIMAL(18, 6) NOT NULL,
    trade_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);

-- Prices
CREATE TABLE IF NOT EXISTS Prices (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id INT NOT NULL,
    price_source VARCHAR(20) DEFAULT 'FILTER',
    price_date TIMESTAMP NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- FX Rates
CREATE TABLE IF NOT EXISTS Fx_Rates (
    currency_pair VARCHAR(7) NOT NULL,
    rate_date TIMESTAMP NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    forward_points DECIMAL(18, 8),
    source VARCHAR(50) DEFAULT 'FILTER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (currency_pair, rate_date)
);

-- Seed test data
INSERT INTO Clients (client_id, client_name) VALUES (100, 'Test Client') ON CONFLICT DO NOTHING;
INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (200, 100, 'Test Fund', 'USD') ON CONFLICT DO NOTHING;
INSERT INTO Accounts (account_id, client_id, fund_id, base_currency, account_number)
VALUES (1001, 100, 200, 'USD', 'ACC-1001') ON CONFLICT DO NOTHING;