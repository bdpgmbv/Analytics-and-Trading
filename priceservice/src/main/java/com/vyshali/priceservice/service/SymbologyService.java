package com.vyshali.priceservice.service;

/*
 * 12/04/2025 - 2:29 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SymbologyService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Resolves a Ticker (e.g., "AAPL") to an Internal ID (e.g., 1002).
     * Cached because Security Master data rarely changes intraday.
     */
    @Cacheable("symbology")
    public Integer resolveTicker(String ticker) {
        // Simple query. In prod, you'd join across ISIN/CUSIP too.
        String sql = "SELECT internal_id FROM Security_Master WHERE ticker = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, ticker);
        } catch (Exception e) {
            // Return null or throw custom exception if ticker unknown
            return null;
        }
    }
}
