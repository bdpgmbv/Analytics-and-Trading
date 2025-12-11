-- liquibase formatted sql
-- changeset arch:positions-loader-columns-1

-- ============================================================
-- POSITIONS TABLE - Add columns needed by Position Loader
-- The original 002-trading-tables.sql created Positions with
-- different columns than what the loader expects.
-- ============================================================

-- Add missing columns that Position Loader uses
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS price DECIMAL(18, 6) DEFAULT 0;
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD';
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS business_date DATE;
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'MSPM';
ALTER TABLE Positions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- ============================================================
-- FIX UNIQUE CONSTRAINT
-- Original: UNIQUE (account_id, product_id, batch_id)
-- Loader expects: UNIQUE (account_id, product_id, business_date)
-- This is needed for the ON CONFLICT clause to work
-- ============================================================

-- Drop old unique constraint (if exists)
DROP INDEX IF EXISTS uq_pos_batch_account_product;

-- Create new unique constraint matching loader's ON CONFLICT clause
-- This allows: INSERT ... ON CONFLICT (account_id, product_id, business_date) DO UPDATE
CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_account_product_date
    ON Positions(account_id, product_id, business_date);

-- ============================================================
-- PERFORMANCE INDEXES
-- These are critical for EOD and query performance
-- ============================================================

-- Primary query pattern: Get positions for account on date
CREATE INDEX IF NOT EXISTS idx_positions_account_date
    ON Positions(account_id, business_date);

-- Secondary: Get all positions for a product (for price updates)
CREATE INDEX IF NOT EXISTS idx_positions_product
    ON Positions(product_id);

-- Batch queries
CREATE INDEX IF NOT EXISTS idx_positions_batch
    ON Positions(batch_id, account_id);