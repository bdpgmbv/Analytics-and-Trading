package com.vyshali.priceservice.repository;

/*
 * 12/02/2025 - 6:51 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FxRepository {
    private final JdbcTemplate jdbcTemplate;

    public void batchInsertFxRates(List<FxRateDTO> rates) {
        String sql = "INSERT INTO FX_Rates (currency_pair, rate_date, rate, forward_points) VALUES (?, ?, ?, ?) ON CONFLICT (currency_pair, rate_date) DO NOTHING";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                FxRateDTO rate = rates.get(i);
                ps.setString(1, rate.currencyPair());
                ps.setTimestamp(2, Timestamp.from(rate.timestamp()));
                ps.setBigDecimal(3, rate.rate());
                ps.setBigDecimal(4, rate.forwardPoints());
            }

            @Override
            public int getBatchSize() {
                return rates.size();
            }
        });
    }
}
