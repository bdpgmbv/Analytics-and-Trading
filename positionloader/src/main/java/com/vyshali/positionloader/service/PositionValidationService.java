package com.vyshali.positionloader.service;

import com.vyshali.positionloader.config.LoaderConfig;
import com.vyshali.common.dto.PositionDto;
import com.vyshali.positionloader.repository.ReferenceDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for validating positions.
 */
@Service
public class PositionValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);
    
    private final ReferenceDataRepository referenceDataRepository;
    private final LoaderConfig config;
    
    public PositionValidationService(
            ReferenceDataRepository referenceDataRepository,
            LoaderConfig config) {
        this.referenceDataRepository = referenceDataRepository;
        this.config = config;
    }
    
    /**
     * Validate a list of positions.
     */
    public ValidationResult validate(List<PositionDto> positions) {
        if (!config.features().validationEnabled()) {
            return ValidationResult.valid();
        }
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> duplicateCheck = new HashSet<>();
        
        for (int i = 0; i < positions.size(); i++) {
            PositionDto position = positions.get(i);
            String prefix = String.format("Position[%d] (product=%d): ", i, position.productId());
            
            // Basic validation
            if (position.accountId() <= 0) {
                errors.add(prefix + "Invalid account ID");
            }
            
            if (position.productId() <= 0) {
                errors.add(prefix + "Invalid product ID");
            }
            
            if (position.businessDate() == null) {
                errors.add(prefix + "Business date is required");
            }
            
            if (position.quantity() == null) {
                errors.add(prefix + "Quantity is required");
            } else if (config.validation().rejectZeroQuantity() && 
                    position.quantity().compareTo(BigDecimal.ZERO) == 0) {
                warnings.add(prefix + "Zero quantity position");
            }
            
            if (position.currency() == null || position.currency().isBlank()) {
                errors.add(prefix + "Currency is required");
            } else if (position.currency().length() != 3) {
                errors.add(prefix + "Invalid currency code: " + position.currency());
            }
            
            // Price threshold check
            if (position.price() != null) {
                BigDecimal threshold = BigDecimal.valueOf(config.validation().maxPriceThreshold());
                if (position.price().abs().compareTo(threshold) > 0) {
                    warnings.add(prefix + "Price exceeds threshold: " + position.price());
                }
            }
            
            // Reference data validation
            if (position.accountId() > 0 && !referenceDataRepository.isAccountActive(position.accountId())) {
                errors.add(prefix + "Account not found or inactive: " + position.accountId());
            }
            
            if (position.productId() > 0 && !referenceDataRepository.isProductValid(position.productId())) {
                errors.add(prefix + "Product not found or invalid: " + position.productId());
            }
            
            // Duplicate check within batch
            String key = position.accountId() + "-" + position.productId() + "-" + position.businessDate();
            if (!duplicateCheck.add(key)) {
                errors.add(prefix + "Duplicate position in batch");
            }
        }
        
        if (!errors.isEmpty()) {
            log.warn("Validation failed with {} errors: {}", errors.size(), errors);
        }
        
        if (!warnings.isEmpty()) {
            log.info("Validation warnings: {}", warnings);
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate a single position.
     */
    public ValidationResult validate(PositionDto position) {
        return validate(List.of(position));
    }
    
    /**
     * Quick check if position is valid (no errors).
     */
    public boolean isValid(PositionDto position) {
        return validate(position).isValid();
    }
    
    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of(), List.of());
        }
        
        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error), List.of());
        }
        
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }
    }
}
