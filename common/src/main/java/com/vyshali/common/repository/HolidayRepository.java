package com.vyshali.common.repository;

import com.vyshali.common.dto.SharedDto.HolidayDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository for holiday calendar operations.
 * Used for business day calculations across all services.
 */
@Repository
public class HolidayRepository {

    private static final Logger log = LoggerFactory.getLogger(HolidayRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public HolidayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Check if a date is a holiday.
     */
    @Cacheable(value = "holidays", key = "#date + '-' + #country")
    public boolean isHoliday(LocalDate date, String country) {
        String sql = "SELECT COUNT(*) FROM holidays WHERE holiday_date = ? AND country = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, country);
        return count != null && count > 0;
    }

    /**
     * Check if date is a half day.
     */
    public boolean isHalfDay(LocalDate date, String country) {
        try {
            String sql = """
                SELECT COUNT(*) FROM holidays 
                WHERE holiday_date = ? AND country = ? AND is_half_day = TRUE
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, country);
            return count != null && count > 0;
        } catch (Exception e) {
            // is_half_day column might not exist
            return false;
        }
    }

    /**
     * Get holidays in a date range.
     */
    public Set<LocalDate> findInRange(LocalDate start, LocalDate end, String country) {
        String sql = """
            SELECT holiday_date FROM holidays 
            WHERE holiday_date BETWEEN ? AND ? AND country = ?
            """;
        List<LocalDate> dates = jdbcTemplate.query(sql, (rs, rowNum) -> 
                rs.getDate("holiday_date").toLocalDate(), start, end, country);
        return new HashSet<>(dates);
    }

    /**
     * Get holidays for a year.
     */
    @Cacheable(value = "yearHolidays", key = "#year + '-' + #country")
    public Set<LocalDate> getForYear(int year, String country) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return findInRange(start, end, country);
    }

    /**
     * Get holiday name.
     */
    public String getHolidayName(LocalDate date, String country) {
        String sql = "SELECT holiday_name FROM holidays WHERE holiday_date = ? AND country = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getString("holiday_name");
            }
            return null;
        }, date, country);
    }

    /**
     * Get all holidays for a country.
     */
    public List<HolidayDTO> getAllForCountry(String country) {
        String sql = """
            SELECT holiday_date, holiday_name, country, 
                   COALESCE(is_half_day, FALSE) as is_half_day
            FROM holidays 
            WHERE country = ?
            ORDER BY holiday_date
            """;
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new HolidayDTO(
                    rs.getDate("holiday_date").toLocalDate(),
                    rs.getString("holiday_name"),
                    rs.getString("country"),
                    rs.getBoolean("is_half_day")
            ), country);
        } catch (Exception e) {
            // Fallback without is_half_day
            String fallbackSql = """
                SELECT holiday_date, holiday_name, country
                FROM holidays WHERE country = ?
                ORDER BY holiday_date
                """;
            return jdbcTemplate.query(fallbackSql, (rs, rowNum) -> new HolidayDTO(
                    rs.getDate("holiday_date").toLocalDate(),
                    rs.getString("holiday_name"),
                    rs.getString("country"),
                    false
            ), country);
        }
    }

    /**
     * Add a holiday.
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays", "businessDays"}, allEntries = true)
    public void addHoliday(LocalDate date, String name, String country) {
        addHoliday(date, name, country, false);
    }

    /**
     * Add a holiday with half-day flag.
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays", "businessDays"}, allEntries = true)
    public void addHoliday(LocalDate date, String name, String country, boolean isHalfDay) {
        try {
            String sql = """
                INSERT INTO holidays (holiday_date, holiday_name, country, is_half_day)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (holiday_date, country) DO UPDATE SET
                    holiday_name = EXCLUDED.holiday_name,
                    is_half_day = EXCLUDED.is_half_day
                """;
            jdbcTemplate.update(sql, date, name, country, isHalfDay);
        } catch (Exception e) {
            // Fallback without is_half_day
            String sql = """
                INSERT INTO holidays (holiday_date, holiday_name, country)
                VALUES (?, ?, ?)
                ON CONFLICT (holiday_date, country) DO UPDATE SET
                    holiday_name = EXCLUDED.holiday_name
                """;
            jdbcTemplate.update(sql, date, name, country);
        }
        log.info("Added holiday: {} on {} for {}", name, date, country);
    }

    /**
     * Remove a holiday.
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays", "businessDays"}, allEntries = true)
    public void removeHoliday(LocalDate date, String country) {
        jdbcTemplate.update("DELETE FROM holidays WHERE holiday_date = ? AND country = ?", 
                date, country);
        log.info("Removed holiday on {} for {}", date, country);
    }

    /**
     * Check if holidays exist for a year.
     */
    public boolean hasHolidaysForYear(int year, String country) {
        String sql = """
            SELECT COUNT(*) FROM holidays 
            WHERE EXTRACT(YEAR FROM holiday_date) = ? AND country = ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, year, country);
        return count != null && count > 0;
    }
}
