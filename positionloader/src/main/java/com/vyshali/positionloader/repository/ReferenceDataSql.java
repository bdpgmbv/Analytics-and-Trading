package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - 12:56 PM
 * @author Vyshali Prabananth Lal
 */

public final class ReferenceDataSql {
    private ReferenceDataSql() {
    }

    public static final String UPSERT_CLIENT = """
                INSERT INTO Clients (client_id, client_name) VALUES (?, ?)
                ON CONFLICT (client_id) DO UPDATE SET client_name = EXCLUDED.client_name
            """;

    public static final String UPSERT_FUND = """
                INSERT INTO Funds (fund_id, client_id, fund_name, base_currency) VALUES (?, ?, ?, ?)
                ON CONFLICT (fund_id) DO UPDATE 
                SET fund_name = EXCLUDED.fund_name, base_currency = EXCLUDED.base_currency
            """;

    public static final String UPSERT_ACCOUNT = """
                INSERT INTO Accounts (account_id, fund_id, account_number, account_type) VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE 
                SET account_number = EXCLUDED.account_number, account_type = EXCLUDED.account_type
            """;

    public static final String UPSERT_PRODUCT = """
                INSERT INTO Products (product_id, ticker, asset_class, description) VALUES (?, ?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE 
                SET ticker = EXCLUDED.ticker, description = EXCLUDED.description
            """;
}
