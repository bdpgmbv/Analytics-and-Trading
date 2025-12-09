-- liquibase formatted sql
-- changeset arch:add-missing-columns-1

-- ============================================================
-- POSITIONS TABLE - Add Bitemporal Columns
-- Referenced by: PositionSql.CLOSE_VERSION, PositionSql.INSERT_VERSION
-- ============================================================
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS system_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS system_to TIMESTAMP DEFAULT '9999-12-31 23:59:59';
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS market_value_base DECIMAL(18, 6) DEFAULT 0;

-- Index for bitemporal queries
CREATE INDEX IF NOT EXISTS idx_positions_system_time ON Positions(account_id, product_id, system_to);

-- ============================================================
-- PRODUCTS TABLE - Add Missing Columns
-- Referenced by: HedgeSql.GET_HEDGE_POSITIONS
-- ============================================================
ALTER TABLE Products ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(20) DEFAULT 'TICKER';
ALTER TABLE Products ADD COLUMN IF NOT EXISTS identifier_value VARCHAR(50);
ALTER TABLE Products ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3);
ALTER TABLE Products ADD COLUMN IF NOT EXISTS risk_region VARCHAR(50);

-- Update existing products to set identifier_value = ticker
UPDATE Products SET identifier_value = ticker WHERE identifier_value IS NULL;

-- ============================================================
-- TRANSACTIONS TABLE - Add Missing Columns
-- Referenced by: TransactionRepository (maturity_date for FX Forwards)
-- ============================================================
ALTER TABLE Transactions ADD COLUMN IF NOT EXISTS maturity_date DATE;
ALTER TABLE Transactions ADD COLUMN IF NOT EXISTS value_date DATE;
ALTER TABLE Transactions ADD COLUMN IF NOT EXISTS strike_price DECIMAL(18, 8);
ALTER TABLE Transactions ADD COLUMN IF NOT EXISTS settle_date DATE;
ALTER TABLE Transactions ADD COLUMN IF NOT EXISTS cp_id INT;

-- Index for forward maturity alerts
CREATE INDEX IF NOT EXISTS idx_txn_maturity ON Transactions(account_id, maturity_date)
    WHERE maturity_date IS NOT NULL;