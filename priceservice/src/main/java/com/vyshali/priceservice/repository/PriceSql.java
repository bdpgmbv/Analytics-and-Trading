package com.vyshali.priceservice.repository;

/*
 * 12/09/2025 - 12:57 PM
 * @author Vyshali Prabananth Lal
 */

public final class PriceSql {
    private PriceSql() {
    }

    public static final String INSERT_PRICE = """
                INSERT INTO Prices (product_id, price_source, price_date, price_value) 
                VALUES (?, ?, ?, ?)
            """;

    public static final String GET_LATEST_PRICES = """
                SELECT DISTINCT ON (product_id) product_id, price_value, price_date 
                FROM Prices 
                ORDER BY product_id, price_date DESC
            """;
}
