package com.vyshali.positionloader.repository;

/*
 * 12/09/2025 - 12:55 PM
 * @author Vyshali Prabananth Lal
 */

public final class TransactionSql {
    private TransactionSql() {
    }

    public static final String INSERT_TXN = """
                INSERT INTO Transactions (
                    transaction_id, account_id, product_id, txn_type, trade_date, 
                    quantity, price, cost_local, external_ref_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (external_ref_id) DO NOTHING
            """;

    public static final String FIND_QTY_BY_REF = "SELECT quantity FROM Transactions WHERE external_ref_id = ?";
}