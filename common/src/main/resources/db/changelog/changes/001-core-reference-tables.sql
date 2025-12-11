-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 001: Core Reference Tables
-- Author: fxanalyzer
-- Description: Clients, Funds, Accounts, Products, Security Master
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:001-clients
CREATE TABLE IF NOT EXISTS clients (
    client_id       INT PRIMARY KEY,
    client_name     VARCHAR(100) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);
-- rollback DROP TABLE clients;

-- changeset fxanalyzer:001-funds
CREATE TABLE IF NOT EXISTS funds (
    fund_id         INT PRIMARY KEY,
    client_id       INT NOT NULL,
    fund_name       VARCHAR(100) NOT NULL,
    base_currency   VARCHAR(3) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_funds_client FOREIGN KEY (client_id) REFERENCES clients(client_id)
);
CREATE INDEX IF NOT EXISTS idx_funds_client ON funds(client_id);
CREATE INDEX IF NOT EXISTS idx_funds_status ON funds(status);
-- rollback DROP TABLE funds;

-- changeset fxanalyzer:001-accounts
CREATE TABLE IF NOT EXISTS accounts (
    account_id      INT PRIMARY KEY,
    client_id       INT NOT NULL,
    client_name     VARCHAR(100),
    fund_id         INT NOT NULL,
    fund_name       VARCHAR(100),
    base_currency   VARCHAR(3) NOT NULL,
    account_number  VARCHAR(50) UNIQUE NOT NULL,
    account_type    VARCHAR(20),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_fund FOREIGN KEY (fund_id) REFERENCES funds(fund_id)
);
CREATE INDEX IF NOT EXISTS idx_accounts_client ON accounts(client_id);
CREATE INDEX IF NOT EXISTS idx_accounts_fund ON accounts(fund_id);
-- rollback DROP TABLE accounts;

-- changeset fxanalyzer:001-products
CREATE TABLE IF NOT EXISTS products (
    product_id          INT PRIMARY KEY,
    ticker              VARCHAR(20) NOT NULL,
    asset_class         VARCHAR(20),
    issue_currency      VARCHAR(3),
    settlement_currency VARCHAR(3),
    description         VARCHAR(255),
    identifier_type     VARCHAR(20) DEFAULT 'TICKER',
    identifier_value    VARCHAR(50),
    risk_region         VARCHAR(50),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_products_ticker ON products(ticker);
CREATE INDEX IF NOT EXISTS idx_products_asset_class ON products(asset_class);
-- rollback DROP TABLE products;

-- changeset fxanalyzer:001-security-master
CREATE TABLE IF NOT EXISTS security_master (
    internal_id     INT PRIMARY KEY,
    ticker          VARCHAR(20) UNIQUE,
    isin            VARCHAR(12),
    cusip           VARCHAR(9),
    sedol           VARCHAR(7),
    description     VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_security_master_isin ON security_master(isin);
CREATE INDEX IF NOT EXISTS idx_security_master_cusip ON security_master(cusip);
-- rollback DROP TABLE security_master;

-- changeset fxanalyzer:001-counterparties
CREATE TABLE IF NOT EXISTS counterparties (
    cp_id           INT PRIMARY KEY,
    cp_name         VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- rollback DROP TABLE counterparties;
