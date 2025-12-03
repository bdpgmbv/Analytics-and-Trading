package com.vyshali.priceservice.repository;

/*
 * 12/02/2025 - 6:51 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.PriceTickDTO;
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
public class PriceRepository {
    private final JdbcTemplate jdbcTemplate;

    public void batchInsertPrices(List<PriceTickDTO> ticks) {
        String sql = "INSERT INTO Prices (product_id, price_source, price_date, price_value) VALUES (?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                PriceTickDTO tick = ticks.get(i);
                ps.setInt(1, tick.productId());
                ps.setString(2, tick.source());
                ps.setTimestamp(3, Timestamp.from(tick.timestamp()));
                ps.setBigDecimal(4, tick.price());
            }

            @Override
            public int getBatchSize() {
                return ticks.size();
            }
        });
    }
}
