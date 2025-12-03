-- liquibase formatted sql
-- changeset tradefill:1

-- 1. Parent Order (The "Master" View)
CREATE TABLE Client_Orders (
    order_id VARCHAR(50) PRIMARY KEY, -- "ORD-101"
    account_id INT NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL, -- BUY/SELL
    total_qty DECIMAL(18, 4),  -- Target Qty (Optional, sometimes unknown in Algo trading)

    status VARCHAR(20), -- 'PARTIAL_FILL', 'FILLED', 'NEW'
    avg_price DECIMAL(18, 6),
    filled_qty DECIMAL(18, 4) DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Individual Fills (The "Detail" View)
CREATE TABLE Execution_Fills (
    exec_id VARCHAR(50) PRIMARY KEY, -- "EXEC-9988"
    order_id VARCHAR(50) NOT NULL,

    fill_qty DECIMAL(18, 4),
    fill_price DECIMAL(18, 6),
    fill_time TIMESTAMP,

    venue VARCHAR(20), -- 'NYSE', 'DARK_POOL'

    FOREIGN KEY (order_id) REFERENCES Client_Orders(order_id)
);

CREATE INDEX idx_fills_order ON Execution_Fills(order_id);