package com.vyshali.positionloader.service;

import com.vyshali.positionloader.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Service for holiday calendar operations (Phase 4 #20).
 */
@Service
public class HolidayService {
    
    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);
    
    private final HolidayRepository holidayRepository;
    
    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }
    
    /**
     * Check if date is a business day.
     */
    @Cacheable(value = "businessDays", key = "#date + '-' + #market")
    public boolean isBusinessDay(LocalDate date, String market) {
        // Check weekend first
        if (isWeekend(date)) {
            return false;
        }
        
        // Check holiday calendar
        return !holidayRepository.isHoliday(date, market);
    }
    
    /**
     * Check if date is a business day (default market).
     */
    public boolean isBusinessDay(LocalDate date) {
        return isBusinessDay(date, "US");
    }
    
    /**
     * Check if date is a weekend.
     */
    public boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
    
    /**
     * Check if date is a holiday.
     */
    @Cacheable(value = "holidays", key = "#date + '-' + #market")
    public boolean isHoliday(LocalDate date, String market) {
        return holidayRepository.isHoliday(date, market);
    }
    
    /**
     * Get previous business day.
     */
    public LocalDate getPreviousBusinessDay(LocalDate date, String market) {
        LocalDate prev = date.minusDays(1);
        while (!isBusinessDay(prev, market)) {
            prev = prev.minusDays(1);
            // Safety limit
            if (prev.isBefore(date.minusMonths(1))) {
                throw new IllegalStateException("Could not find business day within a month");
            }
        }
        return prev;
    }
    
    /**
     * Get previous business day (default market).
     */
    public LocalDate getPreviousBusinessDay(LocalDate date) {
        return getPreviousBusinessDay(date, "US");
    }
    
    /**
     * Get next business day.
     */
    public LocalDate getNextBusinessDay(LocalDate date, String market) {
        LocalDate next = date.plusDays(1);
        while (!isBusinessDay(next, market)) {
            next = next.plusDays(1);
            // Safety limit
            if (next.isAfter(date.plusMonths(1))) {
                throw new IllegalStateException("Could not find business day within a month");
            }
        }
        return next;
    }
    
    /**
     * Get next business day (default market).
     */
    public LocalDate getNextBusinessDay(LocalDate date) {
        return getNextBusinessDay(date, "US");
    }
    
    /**
     * Get holidays for year.
     */
    @Cacheable(value = "yearHolidays", key = "#year + '-' + #market")
    public Set<LocalDate> getHolidaysForYear(int year, String market) {
        return holidayRepository.getHolidaysForYear(year, market);
    }
    
    /**
     * Count business days between two dates.
     */
    public int countBusinessDays(LocalDate startDate, LocalDate endDate, String market) {
        if (startDate.isAfter(endDate)) {
            return 0;
        }
        
        int count = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (isBusinessDay(current, market)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
    
    /**
     * Add business days to a date.
     */
    public LocalDate addBusinessDays(LocalDate date, int days, String market) {
        if (days == 0) {
            return date;
        }
        
        LocalDate result = date;
        int added = 0;
        int direction = days > 0 ? 1 : -1;
        int remaining = Math.abs(days);
        
        while (added < remaining) {
            result = result.plusDays(direction);
            if (isBusinessDay(result, market)) {
                added++;
            }
        }
        return result;
    }
    
    /**
     * Get today's business date (adjusts for weekends/holidays).
     */
    public LocalDate getTodayBusinessDate(String market) {
        LocalDate today = LocalDate.now();
        if (isBusinessDay(today, market)) {
            return today;
        }
        return getPreviousBusinessDay(today, market);
    }
    
    /**
     * Get today's business date (default market).
     */
    public LocalDate getTodayBusinessDate() {
        return getTodayBusinessDate("US");
    }
}
