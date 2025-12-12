package com.vyshali.common.exception;

import com.vyshali.fxanalyzer.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .field(ex.getEntityType())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(PriceNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handlePriceNotAvailable(PriceNotAvailableException ex) {
        log.warn("Price not available: {}", ex.getMessage());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(FxRateNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleFxRateNotAvailable(FxRateNotAvailableException ex) {
        log.warn("FX rate not available: {}", ex.getMessage());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(TradeExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleTradeExecution(TradeExecutionException ex) {
        log.error("Trade execution failed: {} - {}", ex.getExecutionRef(), ex.getMessage());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .field(ex.getExecutionRef())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        
        List<String> details = ex.getErrors().stream()
                .map(ValidationException.ValidationError::toString)
                .collect(Collectors.toList());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .details(details)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Method argument validation failed");
        
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("FXAN-4002")
                .details(details)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errorDetails));
    }

    @ExceptionHandler(FxAnalyzerException.class)
    public ResponseEntity<ApiResponse<Void>> handleFxAnalyzerException(FxAnalyzerException ex) {
        log.error("Application error: {}", ex.getMessage(), ex);
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code(ex.getErrorCode())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error [traceId={}]: {}", traceId, ex.getMessage(), ex);
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("FXAN-9999")
                .traceId(traceId)
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Reference: " + traceId, errorDetails));
    }
}
