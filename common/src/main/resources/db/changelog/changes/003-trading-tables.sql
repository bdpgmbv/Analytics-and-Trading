-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 003: Trading and Transaction Tables
-- Author: fxanalyzer
-- Description: Transactions, Trade Lifecycle, Client Orders, Execution Fills
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:003-transaction-seq
CREATE SEQUENCE IF NOT EXISTS transaction_seq START WITH 1 INCREMENT BY 1;
-- rollback DROP SEQUENCE transaction_seq;

-- changeset fxanalyzer:003-transactions
CREATE TABLE IF NOT EXISTS transactions (
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
    CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_txn_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
CREATE INDEX IF NOT EXISTS idx_txn_account_date ON transactions(account_id, trade_date);
CREATE INDEX IF NOT EXISTS idx_txn_product ON transactions(product_id);
CREATE INDEX IF NOT EXISTS idx_txn_maturity ON transactions(account_id, maturity_date) 
    WHERE maturity_date IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_txn_external_ref ON transactions(external_ref_id);
-- rollback DROP TABLE transactions;

-- changeset fxanalyzer:003-trade-lifecycle
CREATE TABLE IF NOT EXISTS trade_lifecycle (
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
CREATE INDEX IF NOT EXISTS idx_lifecycle_account ON trade_lifecycle(account_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_status ON trade_lifecycle(status);
CREATE INDEX IF NOT EXISTS idx_lifecycle_sent_at ON trade_lifecycle(sent_at);
CREATE INDEX IF NOT EXISTS idx_lifecycle_orphan_check ON trade_lifecycle(status, sent_at)
    WHERE status IN ('SENT', 'ACKNOWLEDGED');
-- rollback DROP TABLE trade_lifecycle;

-- changeset fxanalyzer:003-trade-lifecycle-events
CREATE TABLE IF NOT EXISTS trade_lifecycle_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correlation_id      VARCHAR(36) NOT NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_message       VARCHAR(500),
    error_code          VARCHAR(50),
    event_timestamp     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_events_lifecycle FOREIGN KEY (correlation_id)
        REFERENCES trade_lifecycle(correlation_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_lifecycle_events_correlation ON trade_lifecycle_events(correlation_id);
-- rollback DROP TABLE trade_lifecycle_events;

-- changeset fxanalyzer:003-client-orders
CREATE TABLE IF NOT EXISTS client_orders (
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
CREATE INDEX IF NOT EXISTS idx_client_orders_account ON client_orders(account_id);
CREATE INDEX IF NOT EXISTS idx_client_orders_status ON client_orders(status);
-- rollback DROP TABLE client_orders;

-- changeset fxanalyzer:003-execution-fills
CREATE TABLE IF NOT EXISTS execution_fills (
    exec_id         VARCHAR(50) PRIMARY KEY,
    order_id        VARCHAR(50) NOT NULL,
    fill_qty        DECIMAL(18, 6) NOT NULL,
    fill_price      DECIMAL(18, 6) NOT NULL,
    fill_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    venue           VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fills_order FOREIGN KEY (order_id) REFERENCES client_orders(order_id)
);
CREATE INDEX IF NOT EXISTS idx_fills_order ON execution_fills(order_id);
CREATE INDEX IF NOT EXISTS idx_fills_time ON execution_fills(fill_time DESC);
-- rollback DROP TABLE execution_fills;

-- changeset fxanalyzer:003-trade-fills
CREATE TABLE IF NOT EXISTS trade_fills (
    fill_id         VARCHAR(50) PRIMARY KEY,
    order_id        VARCHAR(50) NOT NULL,
    symbol          VARCHAR(20) NOT NULL,
    side            VARCHAR(10) NOT NULL,
    fill_qty        DECIMAL(18, 6) NOT NULL,
    fill_price      DECIMAL(18, 6) NOT NULL,
    trade_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed       BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_trade_fills_order ON trade_fills(order_id);
CREATE INDEX IF NOT EXISTS idx_trade_fills_processed ON trade_fills(processed) WHERE processed = FALSE;
-- rollback DROP TABLE trade_fills;

-- changeset fxanalyzer:003-hedge-orders
CREATE TABLE IF NOT EXISTS hedge_orders (
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
    CONSTRAINT fk_hedge_orders_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_account ON hedge_orders(account_id);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_status ON hedge_orders(status);
CREATE INDEX IF NOT EXISTS idx_hedge_orders_clordid ON hedge_orders(cl_ord_id);
-- rollback DROP TABLE hedge_orders;
