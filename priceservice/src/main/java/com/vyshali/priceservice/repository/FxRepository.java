package com.vyshali.priceservice.repository;

/*
 * 12/02/2025 - 6:51 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.priceservice.dto.FxRateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class FxRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveRate(FxRateDTO fx) {
        jdbcTemplate.update(FxSql.INSERT_FX,
                fx.pair(),
                Timestamp.from(fx.timestamp()),
                fx.rate()
        );
    }
}