-- liquibase formatted sql
-- changeset positionloader:2
CREATE TABLE Eod_Daily_Status (
    account_id INT NOT NULL,
    business_date DATE NOT NULL,
    client_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, business_date)
);
CREATE INDEX idx_eod_client_date ON Eod_Daily_Status(client_id, business_date);