package com.vyshali.priceservice.service;

/*
 * 12/04/2025 - 2:29 PM
 * @author Vyshali Prabananth Lal
 */

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SymbologyService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Resolves a Ticker (e.g., "AAPL") to an Internal ID (e.g., 1002).
     * Cached because Security Master data rarely changes intraday.
     */
    @Cacheable("symbology")
    public Optional<Integer> resolveTicker(String ticker) {
        String sql = "SELECT internal_id FROM Security_Master WHERE ticker = ?";
        try {
            Integer id = jdbcTemplate.queryForObject(sql, Integer.class, ticker);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty(); // Cleanly handle not found
        }
    }
}