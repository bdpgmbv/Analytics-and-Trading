-- liquibase formatted sql
-- changeset arch:core-schema-1

CREATE TABLE Accounts (
    account_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    client_name VARCHAR(100),
    fund_id INT NOT NULL,
    fund_name VARCHAR(100),
    base_currency VARCHAR(3) NOT NULL,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    account_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    asset_class VARCHAR(20),
    issue_currency VARCHAR(3),
    description VARCHAR(255)
);

CREATE TABLE Security_Master (
    internal_id INT PRIMARY KEY,
    ticker VARCHAR(20) UNIQUE,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    description VARCHAR(255)
);

-- Seed Data
INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name, base_currency, account_number, account_type)
VALUES (1001, 100, 'Apex Capital', 200, 'Global Macro Fund', 'USD', 'ACC-1001', 'MARGIN') ON CONFLICT DO NOTHING;

INSERT INTO Products (product_id, ticker, asset_class, issue_currency, description)
VALUES (2001, 'AAPL', 'EQUITY', 'USD', 'Apple Inc.') ON CONFLICT DO NOTHING;

INSERT INTO Security_Master (internal_id, ticker, description)
VALUES (1000, 'TICKER_1000', 'Mock Asset') ON CONFLICT DO NOTHING;