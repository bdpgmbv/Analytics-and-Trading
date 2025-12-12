package com.vyshali.common.exception;

import java.time.LocalDate;

/**
 * Exception thrown when price is not available for a security.
 * Related to Issue #1: Prices dropping to zero when price service is down.
 */
public class PriceNotAvailableException extends FxAnalyzerException {

    private final String identifier;
    private final LocalDate priceDate;
    
    public PriceNotAvailableException(String identifier, LocalDate priceDate) {
        super("FXAN-2001", String.format("Price not available for %s on %s", identifier, priceDate));
        this.identifier = identifier;
        this.priceDate = priceDate;
    }
    
    public PriceNotAvailableException(String identifier, LocalDate priceDate, Throwable cause) {
        super("FXAN-2001", String.format("Price not available for %s on %s", identifier, priceDate), cause);
        this.identifier = identifier;
        this.priceDate = priceDate;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public LocalDate getPriceDate() {
        return priceDate;
    }
}
