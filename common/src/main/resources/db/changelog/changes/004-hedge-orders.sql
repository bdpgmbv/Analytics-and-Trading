-- liquibase formatted sql
-- changeset arch:hedge-orders-1

CREATE TABLE Hedge_Orders (
    order_id VARCHAR(50) PRIMARY KEY, -- Internal UUID
    account_id INT NOT NULL,
    cl_ord_id VARCHAR(50), -- ID sent to FIX Exchange
    currency_pair VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity DECIMAL(18, 6) NOT NULL,
    tenor VARCHAR(20),
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, FILLED, REJECTED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_hedge_orders_account ON Hedge_Orders(account_id);