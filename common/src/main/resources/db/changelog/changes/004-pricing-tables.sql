-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 004: Pricing Tables
-- Author: fxanalyzer
-- Description: Prices (partitioned), FX Rates
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:004-prices
CREATE TABLE IF NOT EXISTS prices (
    price_id        BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id      INT NOT NULL,
    price_source    VARCHAR(20) DEFAULT 'FILTER',
    price_date      TIMESTAMP NOT NULL,
    price_value     DECIMAL(18, 6) NOT NULL,
    currency        VARCHAR(3) DEFAULT 'USD',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (price_id, price_date)
) PARTITION BY RANGE (price_date);
-- rollback DROP TABLE prices;

-- changeset fxanalyzer:004-prices-default-partition
CREATE TABLE IF NOT EXISTS prices_default PARTITION OF prices DEFAULT;
-- rollback DROP TABLE prices_default;

-- changeset fxanalyzer:004-prices-indexes
CREATE INDEX IF NOT EXISTS idx_prices_product_date ON prices(product_id, price_date DESC);
-- rollback DROP INDEX idx_prices_product_date;

-- changeset fxanalyzer:004-fx-rates
CREATE TABLE IF NOT EXISTS fx_rates (
    currency_pair   VARCHAR(7) NOT NULL,
    rate_date       TIMESTAMP NOT NULL,
    rate            DECIMAL(18, 8) NOT NULL,
    forward_points  DECIMAL(18, 8),
    source          VARCHAR(50) DEFAULT 'FILTER',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (currency_pair, rate_date)
);
CREATE INDEX IF NOT EXISTS idx_fx_rates_pair ON fx_rates(currency_pair);
CREATE INDEX IF NOT EXISTS idx_fx_rates_date ON fx_rates(rate_date DESC);
-- rollback DROP TABLE fx_rates;
