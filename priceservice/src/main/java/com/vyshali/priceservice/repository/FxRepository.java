package com.vyshali.priceservice.repository;

/*
 * 12/10/2025 - FIXED: fx.pair() changed to fx.currencyPair()
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FxRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Save an FX rate to the database.
     */
    public void saveRate(FxRateDTO fx) {
        // FIXED: fx.pair() changed to fx.currencyPair() to match FxRateDTO record
        jdbcTemplate.update(FxSql.INSERT_FX, fx.currencyPair(),  // FIXED: was fx.pair()
                Timestamp.from(fx.timestamp()), fx.rate());
    }

    /**
     * Get latest FX rate for a currency pair.
     */
    public BigDecimal getLatestRate(String currencyPair) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT rate FROM Fx_Rates
                    WHERE currency_pair = ?
                    ORDER BY rate_date DESC
                    LIMIT 1
                    """, BigDecimal.class, currencyPair);
        } catch (Exception e) {
            return BigDecimal.ONE; // Default to 1:1 if not found
        }
    }

    /**
     * Get all latest FX rates.
     */
    public List<FxRateDTO> getAllLatestRates() {
        return jdbcTemplate.query("""
                SELECT DISTINCT ON (currency_pair)
                    currency_pair, rate, forward_points, rate_date, source
                FROM Fx_Rates
                ORDER BY currency_pair, rate_date DESC
                """, (rs, rowNum) -> new FxRateDTO(rs.getString("currency_pair"), rs.getBigDecimal("rate"), rs.getBigDecimal("forward_points"), rs.getTimestamp("rate_date").toInstant(), rs.getString("source")));
    }

    /**
     * Batch insert FX rates.
     */
    public void batchInsertRates(List<FxRateDTO> rates) {
        for (FxRateDTO fx : rates) {
            saveRate(fx);
        }
    }
}