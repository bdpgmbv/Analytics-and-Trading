package com.vyshali.common.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when validation fails
 */
public class ValidationException extends FxAnalyzerException {

    private final List<ValidationError> errors;
    
    public ValidationException(String message) {
        super("FXAN-4001", message);
        this.errors = new ArrayList<>();
    }
    
    public ValidationException(String message, List<ValidationError> errors) {
        super("FXAN-4001", message);
        this.errors = errors != null ? errors : new ArrayList<>();
    }
    
    public ValidationException(String field, String message) {
        super("FXAN-4001", message);
        this.errors = new ArrayList<>();
        this.errors.add(new ValidationError(field, message));
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    public void addError(String field, String message) {
        errors.add(new ValidationError(field, message));
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Validation error details
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s", field, message);
        }
    }
    
    // Factory methods
    public static ValidationException required(String field) {
        return new ValidationException(field, field + " is required");
    }
    
    public static ValidationException invalid(String field, String reason) {
        return new ValidationException(field, field + " is invalid: " + reason);
    }
}
