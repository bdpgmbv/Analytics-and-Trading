-- Simplified Test Schema

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

CREATE TABLE IF NOT EXISTS eod_runs (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
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

CREATE TABLE IF NOT EXISTS Audit_Logs (
    audit_id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50),
    entity_id VARCHAR(100),
    actor VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Test data
INSERT INTO Clients (client_id, client_name) VALUES (100, 'Test Client') ON CONFLICT DO NOTHING;
INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (200, 100, 'Test Fund', 'USD') ON CONFLICT DO NOTHING;
INSERT INTO Accounts (account_id, client_id, fund_id, base_currency, account_number) VALUES (1001, 100, 200, 'USD', 'ACC-1001') ON CONFLICT DO NOTHING;