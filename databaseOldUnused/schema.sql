-- 1. Top Level Client
CREATE TABLE Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL, -- e.g. 'Apex Capital'
    status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 2. The Fund Entity
CREATE TABLE Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100) NOT NULL, -- e.g. 'Global Macro Fund'
    base_currency VARCHAR(3) NOT NULL, -- e.g. 'USD'
    FOREIGN KEY (client_id) REFERENCES Clients(client_id)
);

-- 3. The Custody Accounts (Now linked directly to the Fund)
CREATE TABLE Accounts (
    account_id INT PRIMARY KEY,
    fund_id INT NOT NULL, -- CHANGED: References Fund directly
    account_number VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(20), -- 'CUSTODY', 'MARGIN', 'DVP'
    FOREIGN KEY (fund_id) REFERENCES Funds(fund_id)
);

-- 4. Products
CREATE TABLE Products (
    product_id INT PRIMARY KEY,
    ticker VARCHAR(20),
    security_description VARCHAR(255),
    asset_class VARCHAR(50), -- 'FX_FORWARD', 'EQUITY', 'EQUITY_SWAP'
    issue_currency VARCHAR(3), -- Risk Currency (e.g. BRL)
    settlement_currency VARCHAR(3), -- Payment Currency (e.g. USD)
    risk_region VARCHAR(50) -- e.g. 'EM_LATAM', 'G10'
);

-- 5. Counterparties
CREATE TABLE Counterparties (
    cp_id INT PRIMARY KEY,
    cp_name VARCHAR(100) NOT NULL -- e.g. 'Morgan Stanley', 'Goldman'
);

-- 6. Transactions
CREATE TABLE Transactions (
    transaction_id INT PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,
    cp_id INT,
    txn_type VARCHAR(20) NOT NULL, -- 'BUY', 'SELL', 'ROLL_FORWARD'
    trade_date DATE NOT NULL,
    settle_date DATE,

    -- Specific fields for FX Analyzer Alerts
    maturity_date DATE, -- Triggers the "Forward Maturity Alert" widget
    value_date DATE, -- When the cash flows
    strike_price DECIMAL(18, 8), -- The FX Rate agreed

    quantity DECIMAL(18, 4),
    price DECIMAL(18, 6),

    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id),
    FOREIGN KEY (cp_id) REFERENCES Counterparties(cp_id)
);

-- 7. Positions
CREATE TABLE Positions (
    position_id INT PRIMARY KEY,
    account_id INT NOT NULL,
    product_id INT NOT NULL,

    quantity DECIMAL(18, 4) NOT NULL,

    -- Specific fields for FX Analyzer Position Upload
    source_system VARCHAR(20) DEFAULT 'MSPM', -- 'MSPM', 'MANUAL', 'FTP'
    position_type VARCHAR(50), -- 'PHYSICAL', 'SYNTHETIC'
    is_excluded BOOLEAN DEFAULT FALSE, -- To filter out of "Net Exposure"

    FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    FOREIGN KEY (product_id) REFERENCES Products(product_id)
);

-- 8. FX Rates
CREATE TABLE FX_Rates (
    currency_pair VARCHAR(7) NOT NULL, -- 'EURUSD'
    rate_date DATE NOT NULL,
    rate DECIMAL(18, 8) NOT NULL, -- Spot Rate
    forward_points DECIMAL(18, 8), -- For calculating Forward curves
    PRIMARY KEY (currency_pair, rate_date)
);

-- 9. Prices
CREATE TABLE Prices (
    price_id INT PRIMARY KEY,
    product_id INT NOT NULL,
    price_date DATE NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES Products(product_id)
);