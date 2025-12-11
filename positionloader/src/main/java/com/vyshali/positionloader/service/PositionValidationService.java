package com.vyshali.positionloader.service;

import com.vyshali.positionloader.dto.PositionDto;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for validating position data.
 */
@Service
public class PositionValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);
    
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999");
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999999");
    
    private final ReferenceDataRepository referenceDataRepository;
    
    public PositionValidationService(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }
    
    /**
     * Validate a list of positions.
     */
    public ValidationResult validate(List<PositionDto> positions) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (positions == null || positions.isEmpty()) {
            errors.add("Position list is empty");
            return new ValidationResult(false, errors, warnings);
        }
        
        int invalidCount = 0;
        for (int i = 0; i < positions.size(); i++) {
            PositionDto position = positions.get(i);
            List<String> positionErrors = validatePosition(position, i);
            
            if (!positionErrors.isEmpty()) {
                invalidCount++;
                errors.addAll(positionErrors);
            }
            
            // Add warnings for edge cases
            warnings.addAll(checkWarnings(position, i));
        }
        
        // Check for duplicates
        List<String> duplicateWarnings = checkDuplicates(positions);
        warnings.addAll(duplicateWarnings);
        
        boolean isValid = errors.isEmpty();
        
        if (isValid) {
            log.debug("Validated {} positions successfully", positions.size());
        } else {
            log.warn("Validation failed: {} errors in {} positions", 
                errors.size(), invalidCount);
        }
        
        return new ValidationResult(isValid, errors, warnings);
    }
    
    /**
     * Validate a single position.
     */
    public List<String> validatePosition(PositionDto position, int index) {
        List<String> errors = new ArrayList<>();
        String prefix = "Position[" + index + "]: ";
        
        // Required fields
        if (position.accountId() <= 0) {
            errors.add(prefix + "Invalid account ID");
        }
        
        if (position.productId() <= 0) {
            errors.add(prefix + "Invalid product ID");
        }
        
        if (position.businessDate() == null) {
            errors.add(prefix + "Business date is required");
        }
        
        // Quantity validation
        if (position.quantity() == null) {
            errors.add(prefix + "Quantity is required");
        } else if (position.quantity().abs().compareTo(MAX_QUANTITY) > 0) {
            errors.add(prefix + "Quantity exceeds maximum allowed value");
        }
        
        // Price validation
        if (position.price() != null) {
            if (position.price().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(prefix + "Price cannot be negative");
            } else if (position.price().compareTo(MAX_PRICE) > 0) {
                errors.add(prefix + "Price exceeds maximum allowed value");
            }
        }
        
        // Currency validation
        if (position.currency() == null || position.currency().isBlank()) {
            errors.add(prefix + "Currency is required");
        } else if (position.currency().length() != 3) {
            errors.add(prefix + "Currency must be 3-character ISO code");
        }
        
        // Reference data validation (if reference data is available)
        if (position.accountId() > 0) {
            try {
                if (!referenceDataRepository.isAccountActive(position.accountId())) {
                    errors.add(prefix + "Account " + position.accountId() + " is not active");
                }
            } catch (Exception e) {
                // Reference data not available, skip validation
                log.debug("Skipping account validation: {}", e.getMessage());
            }
        }
        
        if (position.productId() > 0) {
            try {
                if (!referenceDataRepository.isProductValid(position.productId())) {
                    errors.add(prefix + "Product " + position.productId() + " is not valid");
                }
            } catch (Exception e) {
                // Reference data not available, skip validation
                log.debug("Skipping product validation: {}", e.getMessage());
            }
        }
        
        return errors;
    }
    
    /**
     * Check for warning conditions.
     */
    private List<String> checkWarnings(PositionDto position, int index) {
        List<String> warnings = new ArrayList<>();
        String prefix = "Position[" + index + "]: ";
        
        // Zero quantity warning
        if (position.quantity() != null && 
            position.quantity().compareTo(BigDecimal.ZERO) == 0) {
            warnings.add(prefix + "Position has zero quantity");
        }
        
        // Zero price warning
        if (position.price() != null && 
            position.price().compareTo(BigDecimal.ZERO) == 0 &&
            position.quantity() != null &&
            position.quantity().compareTo(BigDecimal.ZERO) != 0) {
            warnings.add(prefix + "Non-zero position has zero price");
        }
        
        // Market value mismatch warning
        if (position.quantity() != null && position.price() != null && 
            position.marketValueLocal() != null) {
            BigDecimal expected = position.quantity().multiply(position.price()).abs();
            BigDecimal actual = position.marketValueLocal().abs();
            BigDecimal diff = expected.subtract(actual).abs();
            
            // Allow 1% tolerance
            if (expected.compareTo(BigDecimal.ZERO) > 0 && 
                diff.divide(expected, 4, java.math.RoundingMode.HALF_UP)
                    .compareTo(new BigDecimal("0.01")) > 0) {
                warnings.add(prefix + "Market value doesn't match qty * price");
            }
        }
        
        return warnings;
    }
    
    /**
     * Check for duplicate positions.
     */
    private List<String> checkDuplicates(List<PositionDto> positions) {
        List<String> warnings = new ArrayList<>();
        
        // Group by account, product, date, type
        java.util.Map<String, Long> counts = positions.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                p -> p.accountId() + "-" + p.productId() + "-" + 
                     p.businessDate() + "-" + p.positionType(),
                java.util.stream.Collectors.counting()
            ));
        
        counts.forEach((key, count) -> {
            if (count > 1) {
                warnings.add("Duplicate position key found: " + key + " (count: " + count + ")");
            }
        });
        
        return warnings;
    }
    
    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings
    ) {
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
        
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }
}
