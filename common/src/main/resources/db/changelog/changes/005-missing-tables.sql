-- liquibase formatted sql
-- changeset arch:missing-tables-1

-- ============================================================
-- 1. CLIENTS TABLE
-- Referenced by: ReferenceDataRepository.ensureClientExists()
-- ============================================================
CREATE TABLE IF NOT EXISTS Clients (
    client_id INT PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_clients_status ON Clients(status);

-- ============================================================
-- 2. FUNDS TABLE
-- Referenced by: ReferenceDataRepository.ensureFundExists()
-- ============================================================
CREATE TABLE IF NOT EXISTS Funds (
    fund_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    fund_name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_funds_client FOREIGN KEY (client_id) REFERENCES Clients(client_id)
);

CREATE INDEX idx_funds_client ON Funds(client_id);

-- ============================================================
-- 3. AUDIT_LOGS TABLE
-- Referenced by: AuditRepository.logEvent()
-- ============================================================
CREATE TABLE IF NOT EXISTS Audit_Logs (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100),
    actor VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_type ON Audit_Logs(event_type);
CREATE INDEX idx_audit_logs_entity ON Audit_Logs(entity_id);
CREATE INDEX idx_audit_logs_actor ON Audit_Logs(actor);
CREATE INDEX idx_audit_logs_created ON Audit_Logs(created_at DESC);

-- ============================================================
-- 4. EOD_DAILY_STATUS TABLE
-- Referenced by: EodTrackerRepository
-- ============================================================
CREATE TABLE IF NOT EXISTS Eod_Daily_Status (
    account_id INT NOT NULL,
    client_id INT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);

CREATE INDEX idx_eod_status_client_date ON Eod_Daily_Status(client_id, business_date);
CREATE INDEX idx_eod_status_status ON Eod_Daily_Status(status);

-- ============================================================
-- 5. POSITION_EXPOSURES TABLE
-- Referenced by: ExposureEnrichmentService, HedgeSql
-- ============================================================
CREATE TABLE IF NOT EXISTS Position_Exposures (
    exposure_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id BIGINT NOT NULL,
    exposure_type VARCHAR(20) NOT NULL,  -- 'GENERIC', 'SPECIFIC_1', 'SPECIFIC_2'
    currency VARCHAR(3) NOT NULL,
    weight DECIMAL(10, 4) NOT NULL,      -- Percentage weight (e.g., 100.00 = 100%)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pos_exposures_position ON Position_Exposures(position_id);
CREATE INDEX idx_pos_exposures_type ON Position_Exposures(exposure_type);

-- ============================================================
-- 6. SHEDLOCK TABLE
-- Required by: ShedLock library for distributed locking
-- ============================================================
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

-- ============================================================
-- 7. FX_RATES TABLE
-- Referenced by: FxRepository in priceservice
-- ============================================================
CREATE TABLE IF NOT EXISTS Fx_Rates (
    currency_pair VARCHAR(7) NOT NULL,   -- e.g., 'EURUSD'
    rate_date TIMESTAMP NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    forward_points DECIMAL(18, 8),
    source VARCHAR(50) DEFAULT 'FILTER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (currency_pair, rate_date)
);

CREATE INDEX idx_fx_rates_pair ON Fx_Rates(currency_pair);
CREATE INDEX idx_fx_rates_date ON Fx_Rates(rate_date DESC);

-- ============================================================
-- 8. CLIENT_ORDERS TABLE
-- Referenced by: TradePersistenceService.updateOrderSummary()
-- ============================================================
CREATE TABLE IF NOT EXISTS Client_Orders (
    order_id VARCHAR(50) PRIMARY KEY,
    account_id INT NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,           -- 'BUY', 'SELL'
    status VARCHAR(20) DEFAULT 'NEW',    -- 'NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED'
    original_qty DECIMAL(18, 6),
    filled_qty DECIMAL(18, 6) DEFAULT 0,
    avg_price DECIMAL(18, 6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_client_orders_account ON Client_Orders(account_id);
CREATE INDEX idx_client_orders_status ON Client_Orders(status);
CREATE INDEX idx_client_orders_ticker ON Client_Orders(ticker);

-- ============================================================
-- 9. EXECUTION_FILLS TABLE
-- Referenced by: TradePersistenceService.saveFill()
-- ============================================================
CREATE TABLE IF NOT EXISTS Execution_Fills (
    exec_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    fill_qty DECIMAL(18, 6) NOT NULL,
    fill_price DECIMAL(18, 6) NOT NULL,
    fill_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    venue VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fills_order FOREIGN KEY (order_id) REFERENCES Client_Orders(order_id)
);

CREATE INDEX idx_execution_fills_order ON Execution_Fills(order_id);
CREATE INDEX idx_execution_fills_time ON Execution_Fills(fill_time DESC);

-- ============================================================
-- SEED DATA
-- ============================================================

-- Seed Clients
INSERT INTO Clients (client_id, client_name, status)
VALUES (100, 'Apex Capital', 'ACTIVE')
ON CONFLICT (client_id) DO NOTHING;

-- Seed Funds
INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status)
VALUES (200, 100, 'Global Macro Fund', 'USD', 'ACTIVE')
ON CONFLICT (fund_id) DO NOTHING;