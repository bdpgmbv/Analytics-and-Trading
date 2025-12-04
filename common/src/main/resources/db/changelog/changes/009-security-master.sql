-- liquibase formatted sql
-- changeset arch:security-master-1

CREATE TABLE Security_Master (
    internal_id INT PRIMARY KEY,
    ticker VARCHAR(20) UNIQUE,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    description VARCHAR(255)
);

-- Seed Data (Matching the Mock Upstream logic of 1000+i)
-- In a real app, this would be loaded via a nightly feed file.
INSERT INTO Security_Master (internal_id, ticker, description) VALUES (1000, 'TICKER_1000', 'Mock Asset 1000');
INSERT INTO Security_Master (internal_id, ticker, description) VALUES (1001, 'EURUSD', 'Euro vs US Dollar');
INSERT INTO Security_Master (internal_id, ticker, description) VALUES (1002, 'AAPL', 'Apple Inc.');