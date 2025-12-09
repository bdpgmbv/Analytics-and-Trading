package com.vyshali.priceservice.repository;

/*
 * 12/09/2025 - 12:58 PM
 * @author Vyshali Prabananth Lal
 */

public final class FxSql {
    private FxSql() {
    }

    public static final String INSERT_FX = """
                INSERT INTO Fx_Rates (currency_pair, rate_date, rate) VALUES (?, ?, ?)
            """;
}
