-- liquibase formatted sql
-- changeset arch:bitemporal-1

-- 1. Add Bitemporal Columns to Transactions
-- valid_from/to: When the trade is effective in the real world (Business Time)
-- system_from/to: When the row is visible in the database (System Time)

ALTER TABLE Transactions ADD COLUMN valid_from TIMESTAMP DEFAULT '1900-01-01 00:00:00';
ALTER TABLE Transactions ADD COLUMN valid_to TIMESTAMP DEFAULT '9999-12-31 23:59:59';

ALTER TABLE Transactions ADD COLUMN system_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE Transactions ADD COLUMN system_to TIMESTAMP DEFAULT '9999-12-31 23:59:59';

-- 2. Update existing rows to be valid 'forever'
UPDATE Transactions SET valid_from = trade_date;

-- 3. Create Indexes for Time-Travel Queries
CREATE INDEX idx_txn_bitemporal_valid ON Transactions(account_id, valid_from, valid_to);
CREATE INDEX idx_txn_bitemporal_system ON Transactions(account_id, system_from, system_to);