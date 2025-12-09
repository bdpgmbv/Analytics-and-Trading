package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - 12:56 PM
 * FIXED: Corrected INSERT statements to match actual schema
 * @author Vyshali Prabananth Lal
 */

public final class ReferenceDataSql {
    private ReferenceDataSql() {
    }

    /**
     * Upsert Client
     * Table: Clients (defined in 005-missing-tables.sql)
     */
    public static final String UPSERT_CLIENT = """
            INSERT INTO Clients (client_id, client_name, status, updated_at) 
            VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP) 
            ON CONFLICT (client_id) DO UPDATE SET 
                client_name = EXCLUDED.client_name,
                updated_at = CURRENT_TIMESTAMP
            """;

    /**
     * Upsert Fund
     * Table: Funds (defined in 005-missing-tables.sql)
     */
    public static final String UPSERT_FUND = """
            INSERT INTO Funds (fund_id, client_id, fund_name, base_currency, status, updated_at) 
            VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP) 
            ON CONFLICT (fund_id) DO UPDATE SET 
                fund_name = EXCLUDED.fund_name, 
                base_currency = EXCLUDED.base_currency,
                updated_at = CURRENT_TIMESTAMP
            """;

    /**
     * Upsert Account
     * Table: Accounts (defined in 001-core-schema.sql)
     * <p>
     * FIXED: The Accounts table has client_id, client_name, fund_id, fund_name, base_currency
     * as required NOT NULL columns. We need to provide all of them.
     * <p>
     * Note: This simplified version uses the fund_id to derive client info.
     * In a real scenario, you'd pass all values from the AccountSnapshotDTO.
     */
    public static final String UPSERT_ACCOUNT = """
            INSERT INTO Accounts (
                account_id, client_id, client_name, fund_id, fund_name, 
                base_currency, account_number, account_type
            ) 
            SELECT 
                ?, f.client_id, c.client_name, ?, f.fund_name, 
                f.base_currency, ?, ?
            FROM Funds f
            JOIN Clients c ON f.client_id = c.client_id
            WHERE f.fund_id = ?
            ON CONFLICT (account_id) DO UPDATE SET 
                account_number = EXCLUDED.account_number, 
                account_type = EXCLUDED.account_type
            """;

    /**
     * Alternative: Direct Upsert Account (when you have all values from DTO)
     */
    public static final String UPSERT_ACCOUNT_DIRECT = """
            INSERT INTO Accounts (
                account_id, client_id, client_name, fund_id, fund_name, 
                base_currency, account_number, account_type
            ) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (account_id) DO UPDATE SET 
                account_number = EXCLUDED.account_number, 
                account_type = EXCLUDED.account_type
            """;

    /**
     * Upsert Product
     * Table: Products (defined in 001-core-schema.sql + 006-add-missing-columns.sql)
     */
    public static final String UPSERT_PRODUCT = """
            INSERT INTO Products (
                product_id, ticker, asset_class, description, 
                identifier_type, identifier_value
            ) 
            VALUES (?, ?, ?, ?, 'TICKER', ?) 
            ON CONFLICT (product_id) DO UPDATE SET 
                ticker = EXCLUDED.ticker, 
                description = EXCLUDED.description,
                identifier_value = EXCLUDED.identifier_value
            """;
}