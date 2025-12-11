-- liquibase formatted sql

-- ═══════════════════════════════════════════════════════════════════════════
-- CHANGESET 009: Seed Data
-- Author: fxanalyzer
-- Description: Reference data for development and testing
-- ═══════════════════════════════════════════════════════════════════════════

-- changeset fxanalyzer:009-seed-clients context:dev,test
INSERT INTO clients (client_id, client_name, status) VALUES
    (1, 'Acme Capital', 'ACTIVE'),
    (2, 'Blue Ridge Investments', 'ACTIVE'),
    (3, 'Cypress Fund Management', 'ACTIVE'),
    (4, 'Delta Hedge Partners', 'ACTIVE'),
    (5, 'Evergreen Asset Management', 'ACTIVE')
ON CONFLICT (client_id) DO NOTHING;
-- rollback DELETE FROM clients WHERE client_id IN (1,2,3,4,5);

-- changeset fxanalyzer:009-seed-funds context:dev,test
INSERT INTO funds (fund_id, client_id, fund_name, base_currency, status) VALUES
    (101, 1, 'Acme Global Equity', 'USD', 'ACTIVE'),
    (102, 1, 'Acme Fixed Income', 'USD', 'ACTIVE'),
    (201, 2, 'Blue Ridge Multi-Strategy', 'USD', 'ACTIVE'),
    (301, 3, 'Cypress Growth Fund', 'EUR', 'ACTIVE'),
    (401, 4, 'Delta FX Overlay', 'USD', 'ACTIVE'),
    (501, 5, 'Evergreen Balanced', 'GBP', 'ACTIVE')
ON CONFLICT (fund_id) DO NOTHING;
-- rollback DELETE FROM funds WHERE fund_id IN (101,102,201,301,401,501);

-- changeset fxanalyzer:009-seed-accounts context:dev,test
INSERT INTO accounts (account_id, client_id, client_name, fund_id, fund_name, base_currency, account_number, account_type) VALUES
    (1001, 1, 'Acme Capital', 101, 'Acme Global Equity', 'USD', 'ACME-EQ-001', 'TRADING'),
    (1002, 1, 'Acme Capital', 102, 'Acme Fixed Income', 'USD', 'ACME-FI-001', 'TRADING'),
    (2001, 2, 'Blue Ridge Investments', 201, 'Blue Ridge Multi-Strategy', 'USD', 'BRID-MS-001', 'TRADING'),
    (3001, 3, 'Cypress Fund Management', 301, 'Cypress Growth Fund', 'EUR', 'CYPR-GR-001', 'TRADING'),
    (4001, 4, 'Delta Hedge Partners', 401, 'Delta FX Overlay', 'USD', 'DELT-FX-001', 'HEDGE'),
    (5001, 5, 'Evergreen Asset Management', 501, 'Evergreen Balanced', 'GBP', 'EVGR-BA-001', 'TRADING')
ON CONFLICT (account_id) DO NOTHING;
-- rollback DELETE FROM accounts WHERE account_id IN (1001,1002,2001,3001,4001,5001);

-- changeset fxanalyzer:009-seed-products context:dev,test
INSERT INTO products (product_id, ticker, asset_class, issue_currency, settlement_currency, description) VALUES
    (1, 'AAPL', 'EQUITY', 'USD', 'USD', 'Apple Inc.'),
    (2, 'MSFT', 'EQUITY', 'USD', 'USD', 'Microsoft Corporation'),
    (3, 'GOOGL', 'EQUITY', 'USD', 'USD', 'Alphabet Inc.'),
    (4, 'AMZN', 'EQUITY', 'USD', 'USD', 'Amazon.com Inc.'),
    (5, 'TSLA', 'EQUITY', 'USD', 'USD', 'Tesla Inc.'),
    (10, 'EURUSD', 'FX', 'EUR', 'USD', 'Euro/US Dollar'),
    (11, 'GBPUSD', 'FX', 'GBP', 'USD', 'British Pound/US Dollar'),
    (12, 'USDJPY', 'FX', 'USD', 'JPY', 'US Dollar/Japanese Yen'),
    (20, 'US10Y', 'FIXED_INCOME', 'USD', 'USD', 'US 10-Year Treasury'),
    (21, 'DE10Y', 'FIXED_INCOME', 'EUR', 'EUR', 'German 10-Year Bund')
ON CONFLICT (product_id) DO NOTHING;
-- rollback DELETE FROM products WHERE product_id IN (1,2,3,4,5,10,11,12,20,21);

-- changeset fxanalyzer:009-seed-holidays-2025 context:dev,test
INSERT INTO holidays (holiday_date, country_code, holiday_name, is_half_day) VALUES
    ('2025-01-01', 'US', 'New Years Day', FALSE),
    ('2025-01-20', 'US', 'Martin Luther King Jr. Day', FALSE),
    ('2025-02-17', 'US', 'Presidents Day', FALSE),
    ('2025-04-18', 'US', 'Good Friday', FALSE),
    ('2025-05-26', 'US', 'Memorial Day', FALSE),
    ('2025-06-19', 'US', 'Juneteenth', FALSE),
    ('2025-07-04', 'US', 'Independence Day', FALSE),
    ('2025-09-01', 'US', 'Labor Day', FALSE),
    ('2025-11-27', 'US', 'Thanksgiving Day', FALSE),
    ('2025-11-28', 'US', 'Day After Thanksgiving', TRUE),
    ('2025-12-24', 'US', 'Christmas Eve', TRUE),
    ('2025-12-25', 'US', 'Christmas Day', FALSE)
ON CONFLICT DO NOTHING;
-- rollback DELETE FROM holidays WHERE country_code = 'US' AND EXTRACT(YEAR FROM holiday_date) = 2025;

-- changeset fxanalyzer:009-seed-counterparties context:dev,test
INSERT INTO counterparties (cp_id, cp_name) VALUES
    (1, 'Goldman Sachs'),
    (2, 'JP Morgan'),
    (3, 'Morgan Stanley'),
    (4, 'Bank of America'),
    (5, 'Citibank')
ON CONFLICT (cp_id) DO NOTHING;
-- rollback DELETE FROM counterparties WHERE cp_id IN (1,2,3,4,5);
