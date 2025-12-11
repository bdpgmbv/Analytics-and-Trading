package com.vyshali.positionloader.service;

import com.vyshali.positionloader.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Service for business day calculations.
 */
@Service
public class BusinessDayService {
    
    private static final Logger log = LoggerFactory.getLogger(BusinessDayService.class);
    private static final String DEFAULT_MARKET = "US";
    
    private final HolidayRepository holidayRepository;
    
    public BusinessDayService(HolidayRepository holidayRepository) {
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
        return isBusinessDay(date, DEFAULT_MARKET);
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
        int iterations = 0;
        while (!isBusinessDay(prev, market)) {
            prev = prev.minusDays(1);
            iterations++;
            // Safety limit
            if (iterations > 30) {
                throw new IllegalStateException("Could not find business day within 30 days");
            }
        }
        return prev;
    }
    
    /**
     * Get previous business day (default market).
     */
    public LocalDate getPreviousBusinessDay(LocalDate date) {
        return getPreviousBusinessDay(date, DEFAULT_MARKET);
    }
    
    /**
     * Get next business day.
     */
    public LocalDate getNextBusinessDay(LocalDate date, String market) {
        LocalDate next = date.plusDays(1);
        int iterations = 0;
        while (!isBusinessDay(next, market)) {
            next = next.plusDays(1);
            iterations++;
            // Safety limit
            if (iterations > 30) {
                throw new IllegalStateException("Could not find business day within 30 days");
            }
        }
        return next;
    }
    
    /**
     * Get next business day (default market).
     */
    public LocalDate getNextBusinessDay(LocalDate date) {
        return getNextBusinessDay(date, DEFAULT_MARKET);
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
     * Count business days (default market).
     */
    public int countBusinessDays(LocalDate startDate, LocalDate endDate) {
        return countBusinessDays(startDate, endDate, DEFAULT_MARKET);
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
     * Add business days (default market).
     */
    public LocalDate addBusinessDays(LocalDate date, int days) {
        return addBusinessDays(date, days, DEFAULT_MARKET);
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
        return getTodayBusinessDate(DEFAULT_MARKET);
    }
    
    /**
     * Add a holiday.
     */
    @CacheEvict(value = {"holidays", "businessDays", "yearHolidays"}, allEntries = true)
    public void addHoliday(LocalDate date, String name, String country) {
        log.info("Adding holiday: {} on {} for {}", name, date, country);
        holidayRepository.addHoliday(date, name, country);
    }
    
    /**
     * Remove a holiday.
     */
    @CacheEvict(value = {"holidays", "businessDays", "yearHolidays"}, allEntries = true)
    public void removeHoliday(LocalDate date, String country) {
        log.info("Removing holiday on {} for {}", date, country);
        holidayRepository.removeHoliday(date, country);
    }
}
