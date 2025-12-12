package com.vyshali.common.service;

import com.vyshali.common.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Service for business day calculations.
 * Used across all services for:
 * - EOD scheduling
 * - T+2 settlement
 * - Holiday detection
 */
@Service
public class BusinessDayService {

    private static final Logger log = LoggerFactory.getLogger(BusinessDayService.class);
    private static final String DEFAULT_MARKET = "US";
    private static final int MAX_ITERATIONS = 30; // Safety limit

    private final HolidayRepository holidayRepository;

    public BusinessDayService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * Check if date is a business day.
     */
    @Cacheable(value = "businessDays", key = "#date + '-' + #market")
    public boolean isBusinessDay(LocalDate date, String market) {
        if (isWeekend(date)) {
            return false;
        }
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
    public boolean isHoliday(LocalDate date, String market) {
        return holidayRepository.isHoliday(date, market);
    }

    /**
     * Check if date is a holiday (default market).
     */
    public boolean isHoliday(LocalDate date) {
        return isHoliday(date, DEFAULT_MARKET);
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
            if (iterations > MAX_ITERATIONS) {
                throw new IllegalStateException(
                        "Could not find business day within " + MAX_ITERATIONS + " days");
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
            if (iterations > MAX_ITERATIONS) {
                throw new IllegalStateException(
                        "Could not find business day within " + MAX_ITERATIONS + " days");
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
     * Calculate T+N settlement date.
     */
    public LocalDate getSettlementDate(LocalDate tradeDate, int settlementDays, String market) {
        return addBusinessDays(tradeDate, settlementDays, market);
    }

    /**
     * Calculate T+2 settlement date (most common for equities).
     */
    public LocalDate getT2SettlementDate(LocalDate tradeDate, String market) {
        return getSettlementDate(tradeDate, 2, market);
    }

    /**
     * Calculate T+2 settlement date (default market).
     */
    public LocalDate getT2SettlementDate(LocalDate tradeDate) {
        return getT2SettlementDate(tradeDate, DEFAULT_MARKET);
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
     * Get today's business date (adjusts if weekend/holiday).
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
     * Get holidays for a year.
     */
    public Set<LocalDate> getHolidaysForYear(int year, String market) {
        return holidayRepository.getForYear(year, market);
    }

    /**
     * Add a holiday.
     */
    public void addHoliday(LocalDate date, String name, String country) {
        log.info("Adding holiday: {} on {} for {}", name, date, country);
        holidayRepository.addHoliday(date, name, country);
    }

    /**
     * Remove a holiday.
     */
    public void removeHoliday(LocalDate date, String country) {
        log.info("Removing holiday on {} for {}", date, country);
        holidayRepository.removeHoliday(date, country);
    }
}
