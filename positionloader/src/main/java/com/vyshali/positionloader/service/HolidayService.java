package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Holiday service (Phase 4 #20).
 * 
 * Handles:
 * - Holiday calendar lookups
 * - Business day calculations
 */
@Service
public class HolidayService {
    
    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);
    private static final String DEFAULT_COUNTRY = "US";
    
    private final HolidayRepository holidayRepository;
    
    public HolidayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }
    
    /**
     * Check if a date is a holiday.
     */
    @Cacheable(value = "holidays", key = "#date.toString() + '-' + #countryCode")
    public boolean isHoliday(LocalDate date, String countryCode) {
        // Weekends are always non-business days
        if (isWeekend(date)) {
            return true;
        }
        
        return holidayRepository.isHoliday(date, countryCode);
    }
    
    /**
     * Check if a date is a holiday (US by default).
     */
    public boolean isHoliday(LocalDate date) {
        return isHoliday(date, DEFAULT_COUNTRY);
    }
    
    /**
     * Check if a date is a weekend.
     */
    public boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
    
    /**
     * Check if a date is a business day.
     */
    public boolean isBusinessDay(LocalDate date) {
        return !isHoliday(date);
    }
    
    /**
     * Get the previous business day.
     */
    public LocalDate getPreviousBusinessDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isBusinessDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }
    
    /**
     * Get the next business day.
     */
    public LocalDate getNextBusinessDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }
    
    /**
     * Get holidays in a date range.
     */
    public Set<LocalDate> getHolidaysInRange(LocalDate start, LocalDate end, String countryCode) {
        return holidayRepository.findHolidaysInRange(start, end, countryCode);
    }
    
    /**
     * Count business days between two dates.
     */
    public int countBusinessDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isBusinessDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
}
