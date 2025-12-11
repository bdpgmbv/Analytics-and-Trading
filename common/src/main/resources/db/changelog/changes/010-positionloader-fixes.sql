-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 010: Position Loader Code Compatibility Fixes
-- Author: fxanalyzer
-- Description: Add missing columns required by Position Loader service
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:010-account-batches-source
-- Position Loader BatchRepository.createBatch() requires 'source' column
ALTER TABLE account_batches ADD COLUMN IF NOT EXISTS source VARCHAR(50);
COMMENT ON COLUMN account_batches.source IS 'Source of batch: EOD, UPLOAD, ADJUSTMENT, INTRADAY, MANUAL';
-- rollback ALTER TABLE account_batches DROP COLUMN source;

-- changeset fxanalyzer:010-accounts-name-status
-- Position Loader ReferenceDataRepository requires 'account_name' and 'status' columns
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_name VARCHAR(100);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
-- Populate account_name from account_number if null
UPDATE accounts SET account_name = COALESCE(account_name, account_number) WHERE account_name IS NULL;
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
-- rollback DROP INDEX IF EXISTS idx_accounts_status; ALTER TABLE accounts DROP COLUMN IF EXISTS status; ALTER TABLE accounts DROP COLUMN IF EXISTS account_name;

-- changeset fxanalyzer:010-products-status
-- Position Loader ReferenceDataRepository.isProductValid() requires 'status' column
ALTER TABLE products ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
-- rollback DROP INDEX IF EXISTS idx_products_status; ALTER TABLE products DROP COLUMN IF EXISTS status;

-- changeset fxanalyzer:010-holidays-country-column
-- Position Loader HolidayRepository uses 'country' but schema has 'country_code'
-- Add 'country' as an alias column and keep both in sync
DO $$
BEGIN
    -- Check if 'country' column exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'holidays' AND column_name = 'country'
    ) THEN
        -- Add country column
        ALTER TABLE holidays ADD COLUMN country VARCHAR(10);
        -- Copy data from country_code
        UPDATE holidays SET country = country_code WHERE country IS NULL;
    END IF;
END $$;

-- Create trigger to keep country and country_code in sync
CREATE OR REPLACE FUNCTION sync_holiday_country()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.country IS NOT NULL AND NEW.country_code IS NULL THEN
        NEW.country_code := NEW.country;
    ELSIF NEW.country_code IS NOT NULL AND NEW.country IS NULL THEN
        NEW.country := NEW.country_code;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_holiday_country ON holidays;
CREATE TRIGGER trg_sync_holiday_country
    BEFORE INSERT OR UPDATE ON holidays
    FOR EACH ROW
    EXECUTE FUNCTION sync_holiday_country();
-- rollback DROP TRIGGER IF EXISTS trg_sync_holiday_country ON holidays; DROP FUNCTION IF EXISTS sync_holiday_country(); ALTER TABLE holidays DROP COLUMN IF EXISTS country;

-- changeset fxanalyzer:010-unique-constraint-holidays
-- Ensure unique constraint works with 'country' column for Position Loader
CREATE UNIQUE INDEX IF NOT EXISTS uq_holidays_date_country_alt 
    ON holidays(holiday_date, country) WHERE country IS NOT NULL;
-- rollback DROP INDEX IF EXISTS uq_holidays_date_country_alt;
