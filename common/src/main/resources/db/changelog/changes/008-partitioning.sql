-- liquibase formatted sql
-- changeset arch:partitioning-1

-- 1. Rename the old non-partitioned table
ALTER TABLE Prices RENAME TO Prices_Old;

-- 2. Create the New Parent Partitioned Table
-- Note: Partition key (price_date) MUST be part of the Primary Key
CREATE TABLE Prices (
    price_id BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id INT NOT NULL,

    price_source VARCHAR(20) DEFAULT 'FILTER',
    price_date TIMESTAMP NOT NULL,
    price_value DECIMAL(18, 6) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (price_id, price_date)
) PARTITION BY RANGE (price_date);

-- 3. Create Monthly Partitions (Example for 2025)
CREATE TABLE Prices_2025_01 PARTITION OF Prices
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE Prices_2025_02 PARTITION OF Prices
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

CREATE TABLE Prices_2025_03 PARTITION OF Prices
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');

CREATE TABLE Prices_2025_04 PARTITION OF Prices
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');

CREATE TABLE Prices_Default PARTITION OF Prices DEFAULT;

-- 4. Create Local Index (Automatically applied to all partitions)
CREATE INDEX idx_prices_product_date ON Prices(product_id, price_date DESC);