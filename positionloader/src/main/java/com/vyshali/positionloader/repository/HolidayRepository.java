package com.vyshali.positionloader.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Repository for holiday calendar operations.
 */
@Repository
public class HolidayRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public HolidayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Check if a date is a holiday.
     */
    public boolean isHoliday(LocalDate date, String countryCode) {
        String sql = "SELECT COUNT(*) FROM holidays WHERE holiday_date = ? AND country_code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, countryCode);
        return count != null && count > 0;
    }
    
    /**
     * Check if a date is a half day.
     */
    public boolean isHalfDay(LocalDate date, String countryCode) {
        String sql = """
            SELECT COUNT(*) FROM holidays 
            WHERE holiday_date = ? AND country_code = ? AND is_half_day = TRUE
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, date, countryCode);
        return count != null && count > 0;
    }
    
    /**
     * Find holidays in a date range.
     */
    public Set<LocalDate> findHolidaysInRange(LocalDate start, LocalDate end, String countryCode) {
        String sql = """
            SELECT holiday_date FROM holidays 
            WHERE holiday_date BETWEEN ? AND ? AND country_code = ?
            """;
        return new HashSet<>(jdbcTemplate.query(sql, (rs, rowNum) -> 
            rs.getDate("holiday_date").toLocalDate(), start, end, countryCode));
    }
    
    /**
     * Get holiday name.
     */
    public String getHolidayName(LocalDate date, String countryCode) {
        String sql = "SELECT holiday_name FROM holidays WHERE holiday_date = ? AND country_code = ?";
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
    public void addHoliday(LocalDate date, String countryCode, String name, boolean isHalfDay) {
        String sql = """
            INSERT INTO holidays (holiday_date, country_code, holiday_name, is_half_day)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (holiday_date, country_code) DO UPDATE SET
                holiday_name = EXCLUDED.holiday_name,
                is_half_day = EXCLUDED.is_half_day
            """;
        jdbcTemplate.update(sql, date, countryCode, name, isHalfDay);
    }
}
