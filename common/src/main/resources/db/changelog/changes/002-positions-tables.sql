-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 002: Positions Tables
-- Author: fxanalyzer
-- Description: Positions, Position Exposures
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:002-positions
CREATE TABLE IF NOT EXISTS positions (
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
    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT fk_positions_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_positions_account_product_date 
    ON positions(account_id, product_id, business_date);
CREATE INDEX IF NOT EXISTS idx_positions_account_date ON positions(account_id, business_date);
CREATE INDEX IF NOT EXISTS idx_positions_product ON positions(product_id);
CREATE INDEX IF NOT EXISTS idx_positions_batch ON positions(batch_id, account_id);
CREATE INDEX IF NOT EXISTS idx_positions_source ON positions(source);
-- rollback DROP TABLE positions;

-- changeset fxanalyzer:002-position-exposures
CREATE TABLE IF NOT EXISTS position_exposures (
    exposure_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position_id     BIGINT NOT NULL,
    exposure_type   VARCHAR(20) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    weight          DECIMAL(10, 4) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exposures_position FOREIGN KEY (position_id) REFERENCES positions(position_id)
);
CREATE INDEX IF NOT EXISTS idx_exposures_position ON position_exposures(position_id);
CREATE INDEX IF NOT EXISTS idx_exposures_type ON position_exposures(exposure_type);
CREATE INDEX IF NOT EXISTS idx_exposures_currency ON position_exposures(currency);
-- rollback DROP TABLE position_exposures;
