package com.vyshali.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Utility class for date operations
 */
public final class DateUtil {

    private DateUtil() {
        // Utility class - no instantiation
    }

    // Time zones
    public static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    public static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");
    public static final ZoneId TOKYO_ZONE = ZoneId.of("Asia/Tokyo");
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    // Formatters
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Business day constants
    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    /**
     * Get current business date (today if weekday, previous Friday if weekend)
     */
    public static LocalDate getCurrentBusinessDate() {
        return getCurrentBusinessDate(LocalDate.now());
    }

    /**
     * Get business date for a given date
     */
    public static LocalDate getCurrentBusinessDate(LocalDate date) {
        if (date == null) return LocalDate.now();
        while (WEEKEND.contains(date.getDayOfWeek())) {
            date = date.minusDays(1);
        }
        return date;
    }

    /**
     * Get next business day
     */
    public static LocalDate getNextBusinessDay(LocalDate date) {
        if (date == null) date = LocalDate.now();
        date = date.plusDays(1);
        while (WEEKEND.contains(date.getDayOfWeek())) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * Get previous business day
     */
    public static LocalDate getPreviousBusinessDay(LocalDate date) {
        if (date == null) date = LocalDate.now();
        date = date.minusDays(1);
        while (WEEKEND.contains(date.getDayOfWeek())) {
            date = date.minusDays(1);
        }
        return date;
    }

    /**
     * Add business days to a date
     */
    public static LocalDate addBusinessDays(LocalDate date, int days) {
        if (date == null) date = LocalDate.now();
        int addedDays = 0;
        int direction = days >= 0 ? 1 : -1;
        int remaining = Math.abs(days);
        
        while (addedDays < remaining) {
            date = date.plusDays(direction);
            if (!WEEKEND.contains(date.getDayOfWeek())) {
                addedDays++;
            }
        }
        return date;
    }

    /**
     * Check if date is a business day
     */
    public static boolean isBusinessDay(LocalDate date) {
        return date != null && !WEEKEND.contains(date.getDayOfWeek());
    }

    /**
     * Calculate days to maturity
     */
    public static int daysToMaturity(LocalDate valueDate) {
        if (valueDate == null) return 0;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), valueDate);
    }

    /**
     * Calculate business days between two dates
     */
    public static int businessDaysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return 0;
        
        int businessDays = 0;
        LocalDate date = startDate;
        
        while (date.isBefore(endDate)) {
            date = date.plusDays(1);
            if (!WEEKEND.contains(date.getDayOfWeek())) {
                businessDays++;
            }
        }
        
        return businessDays;
    }

    /**
     * Get FX spot value date (T+2)
     */
    public static LocalDate getSpotValueDate() {
        return getSpotValueDate(LocalDate.now());
    }

    /**
     * Get FX spot value date (T+2) from a given trade date
     */
    public static LocalDate getSpotValueDate(LocalDate tradeDate) {
        return addBusinessDays(tradeDate, 2);
    }

    /**
     * Get start of day
     */
    public static LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * Get end of day
     */
    public static LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    /**
     * Get NY market open time (9:30 AM ET)
     */
    public static LocalDateTime getNYMarketOpen(LocalDate date) {
        return date.atTime(9, 30).atZone(NEW_YORK_ZONE).toLocalDateTime();
    }

    /**
     * Get NY market close time (4:00 PM ET)
     */
    public static LocalDateTime getNYMarketClose(LocalDate date) {
        return date.atTime(16, 0).atZone(NEW_YORK_ZONE).toLocalDateTime();
    }

    /**
     * Check if current time is within NY market hours
     */
    public static boolean isNYMarketOpen() {
        LocalDateTime now = LocalDateTime.now(NEW_YORK_ZONE);
        LocalDate today = now.toLocalDate();
        
        if (!isBusinessDay(today)) return false;
        
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);
        LocalTime currentTime = now.toLocalTime();
        
        return !currentTime.isBefore(marketOpen) && !currentTime.isAfter(marketClose);
    }

    /**
     * Format date for display
     */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }

    /**
     * Format datetime for display
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "";
    }
}
