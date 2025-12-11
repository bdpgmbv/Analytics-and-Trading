package com.vyshali.positionloader.repository;

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
 * 
 * Note: Schema has 'country_code' column, but we use 'country' for compatibility.
 * The schema changeset 010 adds a 'country' column with a sync trigger.
 */
@Repository
public class HolidayRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    // Column name - use 'country' which is synced with 'country_code' via trigger
    private static final String COUNTRY_COLUMN = "country";
    
    public HolidayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Check if a date is a holiday.
     */
    @Cacheable(value = "holidays", key = "#date + '-' + #countryCode")
    public boolean isHoliday(LocalDate date, String countryCode) {
        String sql = "SELECT COUNT(*) FROM holidays WHERE holiday_date = ? AND " + COUNTRY_COLUMN + " = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, countryCode);
        return count != null && count > 0;
    }
    
    /**
     * Check if a date is a half day.
     */
    public boolean isHalfDay(LocalDate date, String countryCode) {
        try {
            String sql = """
                SELECT COUNT(*) FROM holidays 
                WHERE holiday_date = ? AND %s = ? AND is_half_day = TRUE
                """.formatted(COUNTRY_COLUMN);
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, countryCode);
            return count != null && count > 0;
        } catch (Exception e) {
            // is_half_day column might not exist in older schemas
            return false;
        }
    }
    
    /**
     * Find holidays in a date range.
     */
    public Set<LocalDate> findHolidaysInRange(LocalDate start, LocalDate end, String countryCode) {
        String sql = """
            SELECT holiday_date FROM holidays 
            WHERE holiday_date BETWEEN ? AND ? AND %s = ?
            """.formatted(COUNTRY_COLUMN);
        List<LocalDate> dates = jdbcTemplate.query(sql, (rs, rowNum) -> 
            rs.getDate("holiday_date").toLocalDate(), start, end, countryCode);
        return new HashSet<>(dates);
    }
    
    /**
     * Get holidays for a year.
     */
    @Cacheable(value = "yearHolidays", key = "#year + '-' + #countryCode")
    public Set<LocalDate> getHolidaysForYear(int year, String countryCode) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return findHolidaysInRange(start, end, countryCode);
    }
    
    /**
     * Get holiday name.
     */
    public String getHolidayName(LocalDate date, String countryCode) {
        String sql = "SELECT holiday_name FROM holidays WHERE holiday_date = ? AND " + COUNTRY_COLUMN + " = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return rs.getString("holiday_name");
            }
            return null;
        }, date, countryCode);
    }
    
    /**
     * Add a holiday.
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays"}, allEntries = true)
    public void addHoliday(LocalDate date, String countryCode, String name, boolean isHalfDay) {
        try {
            // Try with is_half_day column
            String sql = """
                INSERT INTO holidays (holiday_date, %s, holiday_name, is_half_day)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (holiday_date, %s) DO UPDATE SET
                    holiday_name = EXCLUDED.holiday_name,
                    is_half_day = EXCLUDED.is_half_day
                """.formatted(COUNTRY_COLUMN, COUNTRY_COLUMN);
            jdbcTemplate.update(sql, date, countryCode, name, isHalfDay);
        } catch (Exception e) {
            // Fallback without is_half_day column (older schema)
            String sql = """
                INSERT INTO holidays (holiday_date, %s, holiday_name)
                VALUES (?, ?, ?)
                ON CONFLICT (holiday_date, %s) DO UPDATE SET
                    holiday_name = EXCLUDED.holiday_name
                """.formatted(COUNTRY_COLUMN, COUNTRY_COLUMN);
            jdbcTemplate.update(sql, date, countryCode, name);
        }
    }
    
    /**
     * Add a holiday (simple version).
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays"}, allEntries = true)
    public void addHoliday(LocalDate date, String name, String countryCode) {
        addHoliday(date, countryCode, name, false);
    }
    
    /**
     * Remove a holiday.
     */
    @Transactional
    @CacheEvict(value = {"holidays", "yearHolidays"}, allEntries = true)
    public void removeHoliday(LocalDate date, String countryCode) {
        String sql = "DELETE FROM holidays WHERE holiday_date = ? AND " + COUNTRY_COLUMN + " = ?";
        jdbcTemplate.update(sql, date, countryCode);
    }
    
    /**
     * Get all holidays for a country.
     */
    public Set<LocalDate> getAllHolidays(String countryCode) {
        String sql = "SELECT holiday_date FROM holidays WHERE " + COUNTRY_COLUMN + " = ?";
        List<LocalDate> dates = jdbcTemplate.query(sql, (rs, rowNum) -> 
            rs.getDate("holiday_date").toLocalDate(), countryCode);
        return new HashSet<>(dates);
    }
    
    /**
     * Check if holiday table has data for a specific year.
     */
    public boolean hasHolidaysForYear(int year, String countryCode) {
        String sql = """
            SELECT COUNT(*) FROM holidays 
            WHERE EXTRACT(YEAR FROM holiday_date) = ? AND %s = ?
            """.formatted(COUNTRY_COLUMN);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, year, countryCode);
        return count != null && count > 0;
    }
    
    /**
     * Get count of holidays for a year.
     */
    public int countHolidaysForYear(int year, String countryCode) {
        String sql = """
            SELECT COUNT(*) FROM holidays 
            WHERE EXTRACT(YEAR FROM holiday_date) = ? AND %s = ?
            """.formatted(COUNTRY_COLUMN);
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, year, countryCode);
        return count != null ? count : 0;
    }
}
