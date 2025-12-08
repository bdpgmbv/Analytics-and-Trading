-- liquibase formatted sql
-- changeset arch:trading-tables-1

-- Transactions (Bitemporal from day 1)
CREATE SEQUENCE position_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE Transactions (
    transaction_id BIGINT PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    txn_type VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    price DECIMAL(18, 6),
    cost_local DECIMAL(18, 6),
    external_ref_id VARCHAR(100) UNIQUE,

    -- Bitemporal Columns
    valid_from TIMESTAMP DEFAULT '1900-01-01 00:00:00',
    valid_to TIMESTAMP DEFAULT '9999-12-31 23:59:59',
    system_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    system_to TIMESTAMP DEFAULT '9999-12-31 23:59:59',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_txn_account_valid ON Transactions(account_id, valid_from, valid_to);

-- Positions (With Batching for Blue/Green)
CREATE TABLE Account_Batches (
    account_id INT NOT NULL,
    batch_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'STAGING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, batch_id)
);

CREATE TABLE Positions (
    position_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    batch_id INT NOT NULL DEFAULT 1,

    quantity DECIMAL(18, 6) NOT NULL DEFAULT 0,
    avg_cost_price DECIMAL(18, 6) DEFAULT 0,
    cost_local DECIMAL(18, 6) DEFAULT 0,
    source_system VARCHAR(20) DEFAULT 'MSPM',
    position_type VARCHAR(20) DEFAULT 'PHYSICAL',

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_pos_batch_account_product ON Positions(account_id, product_id, batch_id);

-- Initialize default batch
INSERT INTO Account_Batches (account_id, batch_id, status) VALUES (1001, 1, 'ACTIVE');

-- Prices (Partitioned)
CREATE TABLE Prices (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id INT NOT NULL,
    price_source VARCHAR(20) DEFAULT 'FILTER',
    price_date TIMESTAMP NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (price_id, price_date)
) PARTITION BY RANGE (price_date);

CREATE TABLE Prices_Default PARTITION OF Prices DEFAULT;
CREATE INDEX idx_prices_product_date ON Prices(product_id, price_date DESC);