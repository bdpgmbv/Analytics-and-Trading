package com.vyshali.common.repository;

import com.vyshali.common.dto.SharedDto.FxRateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FX rate data.
 * Provides currency conversion rates for all services.
 */
@Repository
public class FxRateRepository {

    private static final Logger log = LoggerFactory.getLogger(FxRateRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public FxRateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get rate for a currency pair on a specific date.
     */
    @Cacheable(value = "fxRates", key = "#currencyPair + '-' + #date")
    public Optional<FxRateDTO> getRate(String currencyPair, LocalDate date) {
        String sql = """
            SELECT currency_pair, rate, bid, ask, rate_date, source
            FROM fx_rates 
            WHERE currency_pair = ? AND rate_date <= ?
            ORDER BY rate_date DESC 
            LIMIT 1
            """;
        
        List<FxRateDTO> results = jdbcTemplate.query(sql, (rs, rowNum) -> new FxRateDTO(
                rs.getString("currency_pair"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("bid"),
                rs.getBigDecimal("ask"),
                rs.getTimestamp("rate_date") != null 
                        ? rs.getTimestamp("rate_date").toLocalDateTime() : null,
                rs.getString("source")
        ), currencyPair, date);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Get latest rate for a currency pair.
     */
    public Optional<FxRateDTO> getLatestRate(String currencyPair) {
        return getRate(currencyPair, LocalDate.now());
    }

    /**
     * Get rate as BigDecimal (convenience method).
     */
    public BigDecimal getRateValue(String currencyPair, LocalDate date) {
        return getRate(currencyPair, date)
                .map(FxRateDTO::rate)
                .orElse(BigDecimal.ONE); // Default to 1 if not found
    }

    /**
     * Get all rates for a date.
     */
    public List<FxRateDTO> getRatesForDate(LocalDate date) {
        String sql = """
            SELECT DISTINCT ON (currency_pair) 
                   currency_pair, rate, bid, ask, rate_date, source
            FROM fx_rates 
            WHERE rate_date <= ?
            ORDER BY currency_pair, rate_date DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new FxRateDTO(
                rs.getString("currency_pair"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("bid"),
                rs.getBigDecimal("ask"),
                rs.getTimestamp("rate_date").toLocalDateTime(),
                rs.getString("source")
        ), date);
    }

    /**
     * Save or update an FX rate.
     */
    @Transactional
    @CacheEvict(value = "fxRates", key = "#rate.currencyPair() + '-' + #date")
    public void saveRate(FxRateDTO rate, LocalDate date) {
        String sql = """
            INSERT INTO fx_rates (currency_pair, rate, bid, ask, rate_date, source, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (currency_pair, rate_date) DO UPDATE SET
                rate = EXCLUDED.rate,
                bid = EXCLUDED.bid,
                ask = EXCLUDED.ask,
                source = EXCLUDED.source
            """;

        jdbcTemplate.update(sql,
                rate.currencyPair(),
                rate.rate(),
                rate.bid(),
                rate.ask(),
                date,
                rate.source()
        );
    }

    /**
     * Save rate (simple version).
     */
    @Transactional
    public void saveRate(String currencyPair, BigDecimal rate, LocalDate date, String source) {
        saveRate(new FxRateDTO(currencyPair, rate, rate, rate, LocalDateTime.now(), source), date);
    }

    /**
     * Batch save rates.
     */
    @Transactional
    @CacheEvict(value = "fxRates", allEntries = true)
    public int saveRates(List<FxRateDTO> rates, LocalDate date) {
        String sql = """
            INSERT INTO fx_rates (currency_pair, rate, bid, ask, rate_date, source, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (currency_pair, rate_date) DO UPDATE SET
                rate = EXCLUDED.rate,
                bid = EXCLUDED.bid,
                ask = EXCLUDED.ask,
                source = EXCLUDED.source
            """;

        List<Object[]> batchArgs = rates.stream()
                .map(r -> new Object[]{
                        r.currencyPair(), r.rate(), r.bid(), r.ask(), date, r.source()
                })
                .toList();

        int[] results = jdbcTemplate.batchUpdate(sql, batchArgs);

        int count = 0;
        for (int r : results) {
            if (r >= 0 || r == -2) count++;
        }
        return count;
    }

    /**
     * Get rate history for a currency pair.
     */
    public List<FxRateDTO> getRateHistory(String currencyPair, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT currency_pair, rate, bid, ask, rate_date, source
            FROM fx_rates 
            WHERE currency_pair = ? AND rate_date BETWEEN ? AND ?
            ORDER BY rate_date DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new FxRateDTO(
                rs.getString("currency_pair"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("bid"),
                rs.getBigDecimal("ask"),
                rs.getTimestamp("rate_date").toLocalDateTime(),
                rs.getString("source")
        ), currencyPair, startDate, endDate);
    }

    /**
     * Get all available currency pairs.
     */
    public List<String> getAllCurrencyPairs() {
        String sql = "SELECT DISTINCT currency_pair FROM fx_rates ORDER BY currency_pair";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Purge old rates.
     */
    @Transactional
    public int purgeOlderThan(int days) {
        String sql = "DELETE FROM fx_rates WHERE rate_date < CURRENT_DATE - ?";
        return jdbcTemplate.update(sql, days);
    }
}
