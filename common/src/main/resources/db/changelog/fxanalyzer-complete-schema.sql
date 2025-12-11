-- liquibase formatted sql
-- changeset fxanalyzer:complete-schema-v1

CREATE TABLE Clients (
    client_id       INT PRIMARY KEY,
    client_name     VARCHAR(100) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_clients_status ON Clients(status);


CREATE TABLE Funds (
    fund_id         INT PRIMARY KEY,
    client_id       INT NOT NULL,
    fund_name       VARCHAR(100) NOT NULL,
    base_currency   VARCHAR(3) NOT NULL,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,

    CONSTRAINT fk_funds_client FOREIGN KEY (client_id) REFERENCES Clients(client_id)
);

CREATE INDEX idx_funds_client ON Funds(client_id);
CREATE INDEX idx_funds_status ON Funds(status);


CREATE TABLE Accounts (
    account_id      INT PRIMARY KEY,
    client_id       INT NOT NULL,
    client_name     VARCHAR(100),
    fund_id         INT NOT NULL,
    fund_name       VARCHAR(100),
    base_currency   VARCHAR(3) NOT NULL,
    account_number  VARCHAR(50) UNIQUE NOT NULL,
    account_type    VARCHAR(20),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_fund FOREIGN KEY (fund_id) REFERENCES Funds(fund_id)
);

CREATE INDEX idx_accounts_client ON Accounts(client_id);
CREATE INDEX idx_accounts_fund ON Accounts(fund_id);


CREATE TABLE Products (
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

CREATE INDEX idx_products_ticker ON Products(ticker);
CREATE INDEX idx_products_asset_class ON Products(asset_class);


CREATE TABLE Security_Master (
    internal_id     INT PRIMARY KEY,
    ticker          VARCHAR(20) UNIQUE,
    isin            VARCHAR(12),
    cusip           VARCHAR(9),
    sedol           VARCHAR(7),
    description     VARCHAR(255)
);

CREATE INDEX idx_security_master_isin ON Security_Master(isin);
CREATE INDEX idx_security_master_cusip ON Security_Master(cusip);


CREATE TABLE Positions (
    position_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          INT NOT NULL,
    product_id          INT NOT NULL,
    business_date       DATE NOT NULL,
    quantity            DECIMAL(18, 6) NOT NULL DEFAULT 0,
    price               DECIMAL(18, 6) DEFAULT 0,
    currency            VARCHAR(3) DEFAULT 'USD',
    market_value_local  DECIMAL(18, 6) DEFAULT 0,
    market_value_base   DECIMAL(18, 6) DEFAULT 0,
    avg_cost_price      DECIMAL(18, 6) DEFAULT 0,
    cost_local          DECIMAL(18, 6) DEFAULT 0,
    batch_id            INT DEFAULT 1,
    source              VARCHAR(20) DEFAULT 'MSPM',
    position_type       VARCHAR(20) DEFAULT 'PHYSICAL',
    is_excluded         BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    system_from         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    system_to           TIMESTAMP DEFAULT '9999-12-31 23:59:59',

    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    CONSTRAINT fk_positions_product FOREIGN KEY (product_id) REFERENCES Products(product_id)
);

CREATE UNIQUE INDEX uq_positions_account_product_date ON Positions(account_id, product_id, business_date);
CREATE INDEX idx_positions_account_date ON Positions(account_id, business_date);
CREATE INDEX idx_positions_product ON Positions(product_id);
CREATE INDEX idx_positions_batch ON Positions(batch_id, account_id);
CREATE INDEX idx_positions_source ON Positions(source);


CREATE TABLE Position_Exposures (
    exposure_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id     BIGINT NOT NULL,
    exposure_type   VARCHAR(20) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    weight          DECIMAL(10, 4) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_exposures_position FOREIGN KEY (position_id) REFERENCES Positions(position_id)
);

CREATE INDEX idx_exposures_position ON Position_Exposures(position_id);
CREATE INDEX idx_exposures_type ON Position_Exposures(exposure_type);
CREATE INDEX idx_exposures_currency ON Position_Exposures(currency);


CREATE TABLE Account_Batches (
    account_id      INT NOT NULL,
    batch_id        INT NOT NULL,
    status          VARCHAR(20) DEFAULT 'STAGING',
    position_count  INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at    TIMESTAMP,

    PRIMARY KEY (account_id, batch_id)
);

CREATE INDEX idx_batches_status ON Account_Batches(status);


CREATE SEQUENCE transaction_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE Transactions (
    transaction_id      BIGINT PRIMARY KEY DEFAULT nextval('transaction_seq'),
    account_id          INT NOT NULL,
    product_id          INT NOT NULL,
    cp_id               INT,
    txn_type            VARCHAR(20) NOT NULL,
    trade_date          DATE NOT NULL,
    settle_date         DATE,
    value_date          DATE,
    maturity_date       DATE,
    quantity            DECIMAL(18, 6) NOT NULL,
    price               DECIMAL(18, 6),
    strike_price        DECIMAL(18, 8),
    cost_local          DECIMAL(18, 6),
    external_ref_id     VARCHAR(100) UNIQUE,
    valid_from          TIMESTAMP DEFAULT '1900-01-01 00:00:00',
    valid_to            TIMESTAMP DEFAULT '9999-12-31 23:59:59',
    system_from         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    system_to           TIMESTAMP DEFAULT '9999-12-31 23:59:59',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES Accounts(account_id),
    CONSTRAINT fk_txn_product FOREIGN KEY (product_id) REFERENCES Products(product_id)
);

CREATE INDEX idx_txn_account_date ON Transactions(account_id, trade_date);
CREATE INDEX idx_txn_product ON Transactions(product_id);
CREATE INDEX idx_txn_maturity ON Transactions(account_id, maturity_date) WHERE maturity_date IS NOT NULL;
CREATE INDEX idx_txn_external_ref ON Transactions(external_ref_id);


CREATE TABLE Prices (
    price_id        BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id      INT NOT NULL,
    price_source    VARCHAR(20) DEFAULT 'FILTER',
    price_date      TIMESTAMP NOT NULL,
    price_value     DECIMAL(18, 6) NOT NULL,
    currency        VARCHAR(3) DEFAULT 'USD',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (price_id, price_date)
) PARTITION BY RANGE (price_date);

CREATE TABLE Prices_Default PARTITION OF Prices DEFAULT;

CREATE INDEX idx_prices_product_date ON Prices(product_id, price_date DESC);


CREATE TABLE Fx_Rates (
    currency_pair   VARCHAR(7) NOT NULL,
    rate_date       TIMESTAMP NOT NULL,
    rate            DECIMAL(18, 8) NOT NULL,
    forward_points  DECIMAL(18, 8),
    source          VARCHAR(50) DEFAULT 'FILTER',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (currency_pair, rate_date)
);

CREATE INDEX idx_fx_rates_pair ON Fx_Rates(currency_pair);
CREATE INDEX idx_fx_rates_date ON Fx_Rates(rate_date DESC);


CREATE TABLE Eod_Runs (
    account_id      INT NOT NULL,
    business_date   DATE NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    position_count  INT DEFAULT 0,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id, business_date)
);

CREATE INDEX idx_eod_runs_status ON Eod_Runs(status);
CREATE INDEX idx_eod_runs_date ON Eod_Runs(business_date DESC);


CREATE TABLE Eod_Daily_Status (
    account_id      INT NOT NULL,
    client_id       INT NOT NULL,
    business_date   DATE NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id, business_date)
);

CREATE INDEX idx_eod_status_client_date ON Eod_Daily_Status(client_id, business_date);
CREATE INDEX idx_eod_status_status ON Eod_Daily_Status(status);


CREATE TABLE Hedge_Orders (
    order_id        VARCHAR(50) PRIMARY KEY,
    account_id      INT NOT NULL,
    cl_ord_id       VARCHAR(50),
    currency_pair   VARCHAR(10) NOT NULL,
    side            VARCHAR(10) NOT NULL,
    quantity        DECIMAL(18, 6) NOT NULL,
    tenor           VARCHAR(20),
    limit_price     DECIMAL(18, 8),
    filled_quantity DECIMAL(18, 6) DEFAULT 0,
    filled_price    DECIMAL(18, 8),
    status          VARCHAR(20) DEFAULT 'PENDING',
    reject_reason   VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,

    CONSTRAINT fk_hedge_orders_account FOREIGN KEY (account_id) REFERENCES Accounts(account_id)
);

CREATE INDEX idx_hedge_orders_account ON Hedge_Orders(account_id);
CREATE INDEX idx_hedge_orders_status ON Hedge_Orders(status);
CREATE INDEX idx_hedge_orders_clordid ON Hedge_Orders(cl_ord_id);


CREATE TABLE Trade_Lifecycle (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id      VARCHAR(36) NOT NULL UNIQUE,
    account_id          INT NOT NULL,
    product_id          INT NOT NULL,
    product_ticker      VARCHAR(50),
    side                VARCHAR(10) NOT NULL,
    requested_quantity  DECIMAL(20, 8) NOT NULL,
    requested_price     DECIMAL(20, 8),
    currency            VARCHAR(3),
    status              VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    acknowledged_at     TIMESTAMP WITH TIME ZONE,
    filled_at           TIMESTAMP WITH TIME ZONE,
    rejected_at         TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    filled_quantity     DECIMAL(20, 8),
    filled_price        DECIMAL(20, 8),
    fill_id             VARCHAR(100),
    external_ref_id     VARCHAR(100),
    reject_reason       VARCHAR(500),
    source_system       VARCHAR(50) DEFAULT 'FX_ANALYZER',
    destination_system  VARCHAR(50) DEFAULT 'FX_MATRIX',

    CONSTRAINT chk_lifecycle_status CHECK (status IN (
        'SENT', 'ACKNOWLEDGED', 'FILLED', 'PARTIALLY_FILLED',
        'REJECTED', 'CANCELLED', 'ORPHANED'
    )),
    CONSTRAINT chk_lifecycle_side CHECK (side IN ('BUY', 'SELL'))
);

CREATE INDEX idx_lifecycle_account ON Trade_Lifecycle(account_id);
CREATE INDEX idx_lifecycle_status ON Trade_Lifecycle(status);
CREATE INDEX idx_lifecycle_sent_at ON Trade_Lifecycle(sent_at);
CREATE INDEX idx_lifecycle_orphan_check ON Trade_Lifecycle(status, sent_at)
    WHERE status IN ('SENT', 'ACKNOWLEDGED');


CREATE TABLE Trade_Lifecycle_Events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id      VARCHAR(36) NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_message       VARCHAR(500),
    error_code          VARCHAR(50),
    event_timestamp     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_events_lifecycle FOREIGN KEY (correlation_id)
        REFERENCES Trade_Lifecycle(correlation_id) ON DELETE CASCADE
);

CREATE INDEX idx_lifecycle_events_correlation ON Trade_Lifecycle_Events(correlation_id);


CREATE TABLE Client_Orders (
    order_id        VARCHAR(50) PRIMARY KEY,
    account_id      INT NOT NULL,
    ticker          VARCHAR(20) NOT NULL,
    side            VARCHAR(10) NOT NULL,
    status          VARCHAR(20) DEFAULT 'NEW',
    original_qty    DECIMAL(18, 6),
    filled_qty      DECIMAL(18, 6) DEFAULT 0,
    avg_price       DECIMAL(18, 6),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_client_orders_account ON Client_Orders(account_id);
CREATE INDEX idx_client_orders_status ON Client_Orders(status);


CREATE TABLE Execution_Fills (
    exec_id         VARCHAR(50) PRIMARY KEY,
    order_id        VARCHAR(50) NOT NULL,
    fill_qty        DECIMAL(18, 6) NOT NULL,
    fill_price      DECIMAL(18, 6) NOT NULL,
    fill_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    venue           VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fills_order FOREIGN KEY (order_id) REFERENCES Client_Orders(order_id)
);

CREATE INDEX idx_fills_order ON Execution_Fills(order_id);
CREATE INDEX idx_fills_time ON Execution_Fills(fill_time DESC);


CREATE TABLE Trade_Fills (
    fill_id         VARCHAR(50) PRIMARY KEY,
    order_id        VARCHAR(50) NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(10) NOT NULL,
    fill_qty        DECIMAL(18, 6) NOT NULL,
    fill_price      DECIMAL(18, 6) NOT NULL,
    trade_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_trade_fills_order ON Trade_Fills(order_id);
CREATE INDEX idx_trade_fills_processed ON Trade_Fills(processed) WHERE processed = FALSE;


CREATE TABLE Audit_Logs (
    audit_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    entity_id       VARCHAR(100),
    actor           VARCHAR(100),
    payload         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_type ON Audit_Logs(event_type);
CREATE INDEX idx_audit_entity ON Audit_Logs(entity_id);
CREATE INDEX idx_audit_created ON Audit_Logs(created_at DESC);


CREATE TABLE Ops_Requests (
    request_id      VARCHAR(50) PRIMARY KEY,
    action_type     VARCHAR(50) NOT NULL,
    payload         TEXT,
    status          VARCHAR(20) DEFAULT 'PENDING',
    requested_by    VARCHAR(50) NOT NULL,
    approved_by     VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_ops_requests_status ON Ops_Requests(status);


CREATE TABLE Dlq (
    id              BIGSERIAL PRIMARY KEY,
    topic           VARCHAR(100) NOT NULL,
    message_key     VARCHAR(100),
    payload         TEXT,
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    last_retry_at   TIMESTAMP
);

CREATE INDEX idx_dlq_retry ON Dlq(retry_count, created_at);
CREATE INDEX idx_dlq_topic ON Dlq(topic);


CREATE TABLE Shedlock (
    name            VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until      TIMESTAMP NOT NULL,
    locked_at       TIMESTAMP NOT NULL,
    locked_by       VARCHAR(255) NOT NULL
);


CREATE OR REPLACE VIEW Trade_Reconciliation_Daily AS
SELECT
    DATE(sent_at) as trade_date,
    COUNT(*) as total_trades,
    SUM(CASE WHEN status = 'FILLED' THEN 1 ELSE 0 END) as filled,
    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
    SUM(CASE WHEN status = 'ORPHANED' THEN 1 ELSE 0 END) as orphaned,
    SUM(CASE WHEN status IN ('SENT', 'ACKNOWLEDGED') THEN 1 ELSE 0 END) as pending
FROM Trade_Lifecycle
GROUP BY DATE(sent_at)
ORDER BY trade_date DESC;


CREATE OR REPLACE VIEW Pending_Trades AS
SELECT
    correlation_id,
    account_id,
    product_ticker,
    side,
    requested_quantity,
    status,
    sent_at,
    EXTRACT(EPOCH FROM (NOW() - sent_at)) / 60 as minutes_pending
FROM Trade_Lifecycle
WHERE status IN ('SENT', 'ACKNOWLEDGED')
ORDER BY sent_at ASC;


INSERT INTO Clients (client_id, client_name, status)
VALUES (100, 'Apex Capital', 'ACTIVE')
ON CONFLICT (client_id) DO NOTHING;

INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status)
VALUES (200, 100, 'Global Macro Fund', 'USD', 'ACTIVE')
ON CONFLICT (fund_id) DO NOTHING;

INSERT INTO Accounts (account_id, client_id, client_name, fund_id, fund_name, base_currency, account_number, account_type)
VALUES (1001, 100, 'Apex Capital', 200, 'Global Macro Fund', 'USD', 'MS-APEX-001', 'CUSTODY')
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO Products (product_id, ticker, asset_class, issue_currency, description)
VALUES
    (2001, 'AAPL', 'EQUITY', 'USD', 'Apple Inc.'),
    (2002, 'MSFT', 'EQUITY', 'USD', 'Microsoft Corp.'),
    (2003, 'EURUSD', 'FX', 'EUR', 'Euro/US Dollar Spot')
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO Security_Master (internal_id, ticker, isin, cusip, description)
VALUES
    (1001, 'AAPL', 'US0378331005', '037833100', 'Apple Inc.'),
    (1002, 'MSFT', 'US5949181045', '594918104', 'Microsoft Corp.')
ON CONFLICT (internal_id) DO NOTHING;

INSERT INTO Account_Batches (account_id, batch_id, status)
VALUES (1001, 1, 'ACTIVE')
ON CONFLICT (account_id, batch_id) DO NOTHING;

CREATE TABLE Hedge_Valuations (
    account_id      INT NOT NULL,
    business_date   DATE NOT NULL,
    valuation       DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

CREATE INDEX idx_hedge_val_date ON Hedge_Valuations(business_date);