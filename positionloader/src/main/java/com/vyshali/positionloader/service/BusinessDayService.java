package com.vyshali.positionloader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 4 Enhancement #17: Holiday Calendar Awareness
 * <p>
 * Determines if a date is a business day (not weekend, not holiday).
 * Used to skip EOD processing on non-business days and find previous business day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDayService {

    private final JdbcTemplate jdbc;

    // Cache holidays in memory - refreshed daily
    private Set<LocalDate> holidays = new HashSet<>();
    private int loadedYear = 0;

    @PostConstruct
    public void init() {
        loadHolidays(LocalDate.now().getYear());
    }

    // Refresh holidays at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshHolidays() {
        int year = LocalDate.now().getYear();
        if (year != loadedYear) {
            loadHolidays(year);
        }
    }

    private void loadHolidays(int year) {
        try {
            List<LocalDate> loaded = jdbc.queryForList("SELECT holiday_date FROM holidays WHERE EXTRACT(YEAR FROM holiday_date) IN (?, ?)", LocalDate.class, year, year + 1);

            holidays = new HashSet<>(loaded);
            loadedYear = year;
            log.info("Loaded {} holidays for year {} and {}", holidays.size(), year, year + 1);
        } catch (Exception e) {
            log.warn("Failed to load holidays (table may not exist): {}", e.getMessage());
            // Continue without holidays - weekends still work
        }
    }

    /**
     * Check if a date is a business day.
     * Business day = not weekend AND not holiday.
     */
    public boolean isBusinessDay(LocalDate date) {
        if (date == null) return false;

        // Weekend check
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        // Holiday check
        return !holidays.contains(date);
    }

    /**
     * Get the previous business day.
     * Walks backward from given date until finding a business day.
     */
    public LocalDate getPreviousBusinessDay(LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDate candidate = date.minusDays(1);
        int maxIterations = 10;  // Safety limit

        while (!isBusinessDay(candidate) && maxIterations-- > 0) {
            candidate = candidate.minusDays(1);
        }

        return candidate;
    }

    /**
     * Get the next business day.
     */
    public LocalDate getNextBusinessDay(LocalDate date) {
        if (date == null) date = LocalDate.now();

        LocalDate candidate = date.plusDays(1);
        int maxIterations = 10;

        while (!isBusinessDay(candidate) && maxIterations-- > 0) {
            candidate = candidate.plusDays(1);
        }

        return candidate;
    }

    /**
     * Get the current or previous business day.
     * If today is a business day, returns today.
     * Otherwise returns the previous business day.
     */
    public LocalDate getCurrentOrPreviousBusinessDay() {
        LocalDate today = LocalDate.now();
        return isBusinessDay(today) ? today : getPreviousBusinessDay(today);
    }

    /**
     * Check if EOD should run today.
     * Returns false on weekends and holidays.
     */
    public boolean shouldRunEodToday() {
        return isBusinessDay(LocalDate.now());
    }

    /**
     * Get the business date for EOD processing.
     * Usually today, but could be previous day if running after midnight.
     */
    public LocalDate getEodBusinessDate() {
        return getCurrentOrPreviousBusinessDay();
    }

    /**
     * Add a holiday (for testing or manual additions).
     */
    public void addHoliday(LocalDate date, String name, String country) {
        jdbc.update("INSERT INTO holidays (holiday_date, holiday_name, country) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", date, name, country);
        holidays.add(date);
        log.info("Added holiday: {} - {}", date, name);
    }

    /**
     * Get count of business days between two dates.
     */
    public int getBusinessDayCount(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isBusinessDay(current)) count++;
            current = current.plusDays(1);
        }
        return count;
    }
}