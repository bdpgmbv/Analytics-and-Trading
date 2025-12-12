package com.vyshali.common.util;

import com.vyshali.common.exception.CommonExceptions.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Common validation utilities.
 * 
 * Usage:
 * <pre>
 * ValidationUtils.validate()
 *     .notNull(accountId, "accountId")
 *     .positive(quantity, "quantity")
 *     .notBlank(currency, "currency")
 *     .matches(currency, "[A-Z]{3}", "currency must be 3-letter code")
 *     .throwIfInvalid();
 * </pre>
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    /**
     * Start a validation chain.
     */
    public static Validator validate() {
        return new Validator();
    }

    /**
     * Quick check - throws immediately if null.
     */
    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new ValidationException(field + " cannot be null");
        }
        return value;
    }

    /**
     * Quick check - throws immediately if blank.
     */
    public static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " cannot be blank");
        }
        return value;
    }

    /**
     * Check if string is a valid currency code (3 uppercase letters).
     */
    public static boolean isValidCurrency(String currency) {
        return currency != null && currency.matches("[A-Z]{3}");
    }

    /**
     * Check if string is a valid ticker symbol.
     */
    public static boolean isValidTicker(String ticker) {
        return ticker != null && ticker.matches("[A-Z0-9]{1,10}");
    }

    /**
     * Validator builder for chaining validations.
     */
    public static class Validator {
        private final List<String> errors = new ArrayList<>();

        /**
         * Validate not null.
         */
        public Validator notNull(Object value, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            }
            return this;
        }

        /**
         * Validate string not blank.
         */
        public Validator notBlank(String value, String field) {
            if (value == null || value.isBlank()) {
                errors.add(field + " cannot be blank");
            }
            return this;
        }

        /**
         * Validate string not empty.
         */
        public Validator notEmpty(String value, String field) {
            if (value == null || value.isEmpty()) {
                errors.add(field + " cannot be empty");
            }
            return this;
        }

        /**
         * Validate collection not empty.
         */
        public Validator notEmpty(Collection<?> value, String field) {
            if (value == null || value.isEmpty()) {
                errors.add(field + " cannot be empty");
            }
            return this;
        }

        /**
         * Validate positive number.
         */
        public Validator positive(Number value, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.doubleValue() <= 0) {
                errors.add(field + " must be positive");
            }
            return this;
        }

        /**
         * Validate non-negative number.
         */
        public Validator nonNegative(Number value, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.doubleValue() < 0) {
                errors.add(field + " cannot be negative");
            }
            return this;
        }

        /**
         * Validate BigDecimal positive.
         */
        public Validator positive(BigDecimal value, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(field + " must be positive");
            }
            return this;
        }

        /**
         * Validate number in range.
         */
        public Validator inRange(Number value, Number min, Number max, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.doubleValue() < min.doubleValue() 
                    || value.doubleValue() > max.doubleValue()) {
                errors.add(field + " must be between " + min + " and " + max);
            }
            return this;
        }

        /**
         * Validate string length.
         */
        public Validator length(String value, int min, int max, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.length() < min || value.length() > max) {
                errors.add(field + " length must be between " + min + " and " + max);
            }
            return this;
        }

        /**
         * Validate exact string length.
         */
        public Validator exactLength(String value, int length, String field) {
            if (value == null) {
                errors.add(field + " cannot be null");
            } else if (value.length() != length) {
                errors.add(field + " must be exactly " + length + " characters");
            }
            return this;
        }

        /**
         * Validate string matches pattern.
         */
        public Validator matches(String value, String regex, String message) {
            if (value != null && !Pattern.matches(regex, value)) {
                errors.add(message);
            }
            return this;
        }

        /**
         * Validate currency code.
         */
        public Validator validCurrency(String value, String field) {
            if (value != null && !isValidCurrency(value)) {
                errors.add(field + " must be a valid 3-letter currency code");
            }
            return this;
        }

        /**
         * Validate date not in past.
         */
        public Validator notInPast(LocalDate value, String field) {
            if (value != null && value.isBefore(LocalDate.now())) {
                errors.add(field + " cannot be in the past");
            }
            return this;
        }

        /**
         * Validate date not in future.
         */
        public Validator notInFuture(LocalDate value, String field) {
            if (value != null && value.isAfter(LocalDate.now())) {
                errors.add(field + " cannot be in the future");
            }
            return this;
        }

        /**
         * Add custom validation.
         */
        public Validator check(boolean condition, String message) {
            if (!condition) {
                errors.add(message);
            }
            return this;
        }

        /**
         * Check if validation passed.
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Get validation errors.
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Throw ValidationException if any errors.
         */
        public void throwIfInvalid() throws ValidationException {
            if (!errors.isEmpty()) {
                throw new ValidationException("Validation failed", errors);
            }
        }

        /**
         * Get result without throwing.
         */
        public ValidationResult toResult() {
            return new ValidationResult(errors.isEmpty(), new ArrayList<>(errors));
        }
    }

    /**
     * Validation result.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error));
        }
    }
}
