package com.vyshali.positionloader.service;

import com.fxanalyzer.positionloader.dto.PositionDto;
import com.fxanalyzer.positionloader.repository.ReferenceDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Position validation service.
 * 
 * Validates:
 * - Required fields
 * - Reference data (accounts, products)
 * - Business rules (quantity limits, etc.)
 */
@Service
public class PositionValidationService {
    
    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);
    
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999.999999");
    private static final BigDecimal MIN_QUANTITY = MAX_QUANTITY.negate();
    
    private final ReferenceDataRepository referenceDataRepository;
    
    public PositionValidationService(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }
    
    /**
     * Validate a list of positions.
     * 
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate(List<PositionDto> positions) {
        List<String> errors = new ArrayList<>();
        
        if (positions == null || positions.isEmpty()) {
            errors.add("Position list is empty");
            return errors;
        }
        
        // Check for duplicates
        Set<String> seen = new HashSet<>();
        int index = 0;
        
        for (PositionDto position : positions) {
            List<String> positionErrors = validateSingle(position);
            for (String error : positionErrors) {
                errors.add("Row " + index + ": " + error);
            }
            
            // Check duplicates
            String key = position.accountId() + "-" + position.productId() + "-" + position.businessDate();
            if (!seen.add(key)) {
                errors.add("Row " + index + ": Duplicate position for account " + 
                    position.accountId() + " product " + position.productId());
            }
            
            index++;
        }
        
        // Log summary
        if (!errors.isEmpty()) {
            log.warn("Validation found {} errors in {} positions", errors.size(), positions.size());
        }
        
        return errors;
    }
    
    /**
     * Validate a single position.
     * 
     * @return List of validation errors (empty if valid)
     */
    public List<String> validateSingle(PositionDto position) {
        List<String> errors = new ArrayList<>();
        
        // Required fields
        if (position.accountId() <= 0) {
            errors.add("Invalid account ID: " + position.accountId());
        }
        
        if (position.productId() <= 0) {
            errors.add("Invalid product ID: " + position.productId());
        }
        
        if (position.businessDate() == null) {
            errors.add("Business date is required");
        }
        
        if (position.quantity() == null) {
            errors.add("Quantity is required");
        }
        
        // Quantity bounds
        if (position.quantity() != null) {
            if (position.quantity().compareTo(MAX_QUANTITY) > 0 || 
                position.quantity().compareTo(MIN_QUANTITY) < 0) {
                errors.add("Quantity out of bounds: " + position.quantity());
            }
        }
        
        // Currency format
        if (position.currency() != null && position.currency().length() != 3) {
            errors.add("Invalid currency code: " + position.currency());
        }
        
        // Reference data validation (if no other errors)
        if (errors.isEmpty()) {
            validateReferenceData(position, errors);
        }
        
        return errors;
    }
    
    /**
     * Validate reference data exists.
     */
    private void validateReferenceData(PositionDto position, List<String> errors) {
        // Check account exists
        if (!referenceDataRepository.accountExists(position.accountId())) {
            errors.add("Account not found: " + position.accountId());
        }
        
        // Check product exists
        if (!referenceDataRepository.productExists(position.productId())) {
            errors.add("Product not found: " + position.productId());
        }
    }
    
    /**
     * Validate positions for EOD processing (stricter rules).
     */
    public List<String> validateForEod(List<PositionDto> positions, int expectedAccountId) {
        List<String> errors = validate(positions);
        
        // All positions must be for the same account
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).accountId() != expectedAccountId) {
                errors.add("Row " + i + ": Expected account " + expectedAccountId + 
                    " but found " + positions.get(i).accountId());
            }
        }
        
        return errors;
    }
}
