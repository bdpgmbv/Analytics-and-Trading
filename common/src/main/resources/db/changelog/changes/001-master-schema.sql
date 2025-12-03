-- ==================================================================================
-- 1. REFERENCE DATA (Who and What)
-- ==================================================================================

-- The Top-Level Entity (e.g., "Apex Capital")
CREATE TABLE Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- The Investment Vehicle (e.g., "Global Macro Fund")
CREATE TABLE Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL, -- Critical for 'MV Base' calculations
    FOREIGN KEY (client_id) REFERENCES Clients(client_id)
);

-- The actual Custody/Margin accounts at the Bank
CREATE TABLE Accounts (
    account_id INT PRIMARY KEY,
    fund_id INT NOT NULL,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(20), -- 'CUSTODY', 'MARGIN', 'DVP'
    FOREIGN KEY (fund_id) REFERENCES Funds(fund_id)
);

-- Financial Instruments
CREATE TABLE Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20),
    security_description VARCHAR(255),
    asset_class VARCHAR(50), -- 'FX_FORWARD', 'EQUITY', 'CASH'

    -- Identification (For Image 1 & 3 grids)
    identifier_type VARCHAR(20) DEFAULT 'CUSIP', -- 'CUSIP', 'ISIN', 'SEDOL'
    identifier_value VARCHAR(50),

    -- Risk Attributes
    issue_currency VARCHAR(3), -- Risk Currency (e.g. BRL)
    settlement_currency VARCHAR(3), -- Payment Currency (e.g. USD)
    risk_region VARCHAR(50)
);

-- Trading Partners (For Risk Management)
CREATE TABLE Counterparties (
    cp_id INT PRIMARY KEY,
    cp_name VARCHAR(100) NOT NULL -- e.g. 'Goldman Sachs'
);

-- ==================================================================================
-- 2. TRADING CORE (The Audit Trail & Current State)
-- ==================================================================================

CREATE SEQUENCE position_seq START WITH 1 INCREMENT BY 50;

-- A. TRANSACTIONS (History)
-- Supports 'Transactions' Tab (Image 1)
CREATE TABLE Transactions (
    transaction_id BIGINT PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    cp_id INT,

    -- Trade Details
    txn_type VARCHAR(20) NOT NULL, -- 'BUY', 'SELL', 'SHORT_SELL'
    external_ref_id VARCHAR(100) UNIQUE, -- Idempotency Key from MSPA

    -- Dates (Critical for Cash & Maturity Tabs)
    trade_date DATE NOT NULL,
    settle_date DATE,
    maturity_date DATE, -- Used in 'Forward Maturity Alert'
    value_date DATE,    -- Used in 'Cash Management'

    -- Economics
    quantity DECIMAL(18, 4),
    price DECIMAL(18, 6),       -- Execution Price
    strike_price DECIMAL(18, 8), -- For Options/Forwards

    -- Calculated Costs (For Image 1)
    cost_local DECIMAL(18, 6),
    cost_settle DECIMAL(18, 6),
    realized_pl DECIMAL(18, 2),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id),
    FOREIGN KEY (cp_id) REFERENCES Counterparties(cp_id)
);
-- Index for Maturity Alert queries
CREATE INDEX idx_txn_maturity ON Transactions(maturity_date, account_id);


-- B. POSITIONS (Current State)
-- Supports 'Position Upload' and 'Security Exposure' Tabs
CREATE TABLE Positions (
    position_id BIGINT PRIMARY KEY DEFAULT nextval('position_seq'),
    account_id INT NOT NULL,
    product_id INT NOT NULL,

    -- Core Holding
    quantity DECIMAL(18, 4) NOT NULL,

    -- P&L Basics
    avg_cost_price DECIMAL(18, 8) DEFAULT 0,
    cost_local DECIMAL(18, 6),
    cost_base DECIMAL(18, 6),

    -- Live Valuation (Updated by PriceService)
    market_value_base DECIMAL(18, 6), -- 'MV Base' column
    gain_loss DECIMAL(18, 6),         -- 'Unrealized P&L'

    -- Metadata
    source_system VARCHAR(20) DEFAULT 'MSPM', -- 'MSPM', 'MANUAL' (For Image 3)
    position_type VARCHAR(50),                -- 'PHYSICAL', 'SYNTHETIC'
    price_timing VARCHAR(20),                 -- 'REAL_TIME', 'DELAYED' (For Image 5)
    is_excluded BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP,

    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id),

    -- CRITICAL: Ensures unique holding per product per account
    CONSTRAINT uq_pos_account_product UNIQUE (account_id, product_id)
);

-- C. EXPOSURES (Risk Breakdown)
-- Supports 'Security Exposure' Tab (Image 5)
CREATE TABLE Position_Exposures (
    exposure_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id BIGINT NOT NULL,

    exposure_type VARCHAR(50), -- 'GENERIC' (Currency), 'SPECIFIC_1' (Issuer)
    currency VARCHAR(3),
    weight DECIMAL(5, 2),      -- % Contribution to risk
    exposure_value DECIMAL(18, 6),

    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (position_id) REFERENCES Positions(position_id) ON DELETE CASCADE
);

-- ==================================================================================
-- 3. MARKET DATA (Pricing)
-- ==================================================================================

-- FX Rates for Multi-Currency Valuations
CREATE TABLE FX_Rates (
    currency_pair VARCHAR(7) NOT NULL, -- 'EURUSD'
    rate_date TIMESTAMP NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    forward_points DECIMAL(18, 8), -- For Forward Pricing
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (currency_pair, rate_date)
);

-- Asset Prices (Time Series)
CREATE TABLE Prices (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id INT NOT NULL,

    price_source VARCHAR(20) DEFAULT 'FILTER', -- 'FILTER', 'BLOOMBERG'
    price_date TIMESTAMP NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES Products(product_id)
);

-- Indexes for fast "Latest Price" lookups
CREATE INDEX idx_prices_latest ON Prices(product_id, price_date DESC);
CREATE INDEX idx_fx_latest ON FX_Rates(currency_pair, rate_date DESC);

-- ==================================================================================
-- 4. OPERATIONS & COMPLIANCE
-- ==================================================================================

-- Tracks EOD Load Progress (Sign-off)
CREATE TABLE Eod_Daily_Status (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    client_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date),
    FOREIGN KEY (account_id) REFERENCES Accounts(account_id)
);
CREATE INDEX idx_eod_client_date ON Eod_Daily_Status(client_id, business_date);

-- Distributed Locking (ShedLock)
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Audit Log for Manual Actions
CREATE TABLE Ops_Audit_Log (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL, -- 'TRIGGER_EOD'
    target_id VARCHAR(50),
    triggered_by VARCHAR(100),
    status VARCHAR(20),
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);