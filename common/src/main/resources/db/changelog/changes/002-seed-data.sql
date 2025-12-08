-- liquibase formatted sql
-- changeset admin:2 context:!prod

-- ^^^ FIXED: Added context:!prod. This script will ONLY run in dev/test/local.

-- 1. Clients & Funds
INSERT INTO Clients (client_id, client_name) VALUES (100, 'Apex Capital') ON CONFLICT DO NOTHING;
INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (200, 100, 'Global Macro Fund', 'USD') ON CONFLICT DO NOTHING;

-- 2. Accounts
INSERT INTO Accounts (account_id, fund_id, account_number, account_type)
VALUES (1001, 200, 'ACC-1001', 'CUSTODY') ON CONFLICT DO NOTHING;

-- 3. Products
INSERT INTO Products (product_id, ticker, security_description, asset_class, issue_currency, settlement_currency)
VALUES (1001, 'EURUSD', 'Euro vs US Dollar', 'FX_FORWARD', 'EUR', 'USD') ON CONFLICT DO NOTHING;

INSERT INTO Products (product_id, ticker, security_description, asset_class, issue_currency, settlement_currency)
VALUES (1002, 'AAPL', 'Apple Inc', 'EQUITY', 'USD', 'USD') ON CONFLICT DO NOTHING;

-- 4. Counterparties
INSERT INTO Counterparties (cp_id, cp_name) VALUES (500, 'Morgan Stanley') ON CONFLICT DO NOTHING;
INSERT INTO Counterparties (cp_id, cp_name) VALUES (501, 'Goldman Sachs') ON CONFLICT DO NOTHING;