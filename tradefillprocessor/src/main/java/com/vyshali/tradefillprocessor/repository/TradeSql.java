package com.vyshali.tradefillprocessor.repository;

/*
 * 12/08/2025 - 5:46 PM
 * @author Vyshali Prabananth Lal
 */


public final class TradeSql {

    private TradeSql() {
    }

    public static final String INSERT_FILL = """
                INSERT INTO Trade_Fills (
                    fill_id, order_id, symbol, side, fill_qty, fill_price, processed
                )
                VALUES (?, ?, ?, ?, ?, ?, FALSE)
                ON CONFLICT (fill_id) DO NOTHING
            """;
}
