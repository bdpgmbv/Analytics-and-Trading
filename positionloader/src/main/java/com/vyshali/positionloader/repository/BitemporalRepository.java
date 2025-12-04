package com.vyshali.positionloader.repository;

/*
 * 12/04/2025 - 2:20 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class BitemporalRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Time Travel Query:
     * "What was the total quantity for this product at a specific moment in the past?"
     * * @param businessDate - The date the trade was valid (e.g., "Trade Date")
     *
     * @param systemTime - The time we are querying "As Of" (e.g., "As of 5 PM yesterday")
     */
    public BigDecimal getQuantityAsOf(Integer accountId, Integer productId, LocalDateTime businessDate, LocalDateTime systemTime) {
        String sql = """
                    SELECT COALESCE(SUM(quantity), 0)
                    FROM Transactions
                    WHERE account_id = ? 
                      AND product_id = ?
                      -- Business Validity: Trade must be valid on the requested business date
                      AND valid_from <= ? AND valid_to > ?
                      -- System Visibility: Row must have been visible to the system at that exact time
                      AND system_from <= ? AND system_to > ?
                """;

        Timestamp busTs = Timestamp.valueOf(businessDate);
        Timestamp sysTs = Timestamp.valueOf(systemTime);

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, accountId, productId, busTs, busTs, sysTs, sysTs);
    }
}
