-- liquibase formatted sql

-- changeset positionloader:1
CREATE TABLE Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

CREATE TABLE Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    FOREIGN KEY (client_id) REFERENCES Clients(client_id)
);

CREATE TABLE Accounts (
    account_id INT PRIMARY KEY,
    fund_id INT NOT NULL,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(20),
    FOREIGN KEY (fund_id) REFERENCES Funds(fund_id)
);

CREATE TABLE Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20),
    security_description VARCHAR(255),
    asset_class VARCHAR(50),
    issue_currency VARCHAR(3),
    settlement_currency VARCHAR(3),
    risk_region VARCHAR(50)
);

CREATE TABLE Counterparties (
    cp_id INT PRIMARY KEY,
    cp_name VARCHAR(100) NOT NULL
);

-- NOTE: Using BIGINT for Transactions to prevent overflow
CREATE TABLE Transactions (
    transaction_id BIGINT PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    cp_id INT,
    txn_type VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    settle_date DATE,
    maturity_date DATE,
    value_date DATE,
    strike_price DECIMAL(18, 8),
    quantity DECIMAL(18, 4),
    price DECIMAL(18, 6),
    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id),
    FOREIGN KEY (cp_id) REFERENCES Counterparties(cp_id)
);

-- NOTE: Using BIGINT for Position ID
CREATE SEQUENCE position_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE Positions (
    position_id BIGINT PRIMARY KEY DEFAULT nextval('position_seq'),
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity DECIMAL(18, 4) NOT NULL,
    source_system VARCHAR(20) DEFAULT 'MSPM',
    position_type VARCHAR(50) DEFAULT 'PHYSICAL',
    is_excluded BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id),

    -- CRITICAL: This Unique Constraint enables "Upsert" logic
    CONSTRAINT uq_pos_account_product UNIQUE (account_id, product_id)
);

CREATE TABLE FX_Rates (
    currency_pair VARCHAR(7) NOT NULL,
    rate_date DATE NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    forward_points DECIMAL(18, 8),
    PRIMARY KEY (currency_pair, rate_date)
);

CREATE TABLE Prices (
    price_id BIGINT PRIMARY KEY,
    product_id INT NOT NULL,
    price_date DATE NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES Products(product_id)
);