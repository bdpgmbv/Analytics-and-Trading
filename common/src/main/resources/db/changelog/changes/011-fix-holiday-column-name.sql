-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Fix holiday table column naming inconsistency
-- Issue: Some files use 'country', others use 'country_code'
-- Solution: Standardize on 'country' to match HolidayRepository.java
-- ═══════════════════════════════════════════════════════════════════════════════

-- Only run this if you have a holidays table with 'country_code' column
-- This will rename it to 'country' for consistency

DO $$
BEGIN
    -- Check if country_code column exists and country doesn't
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'holidays' AND column_name = 'country_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'holidays' AND column_name = 'country'
    ) THEN
        -- Rename country_code to country
        ALTER TABLE holidays RENAME COLUMN country_code TO country;
        RAISE NOTICE 'Renamed holidays.country_code to holidays.country';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'holidays' AND column_name = 'country_code'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'holidays' AND column_name = 'country'
    ) THEN
        -- Both exist - drop country_code (assuming country has the data)
        ALTER TABLE holidays DROP COLUMN country_code;
        RAISE NOTICE 'Dropped duplicate holidays.country_code column';
    ELSE
        RAISE NOTICE 'holidays table already has correct column naming';
    END IF;
END $$;

-- Ensure index exists on country column
CREATE INDEX IF NOT EXISTS idx_holidays_country ON holidays(country);
CREATE INDEX IF NOT EXISTS idx_holidays_date ON holidays(holiday_date);
CREATE INDEX IF NOT EXISTS idx_holidays_country_date ON holidays(country, holiday_date);
