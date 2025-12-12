package com.vyshali.common.exception;

import java.time.LocalDate;

/**
 * Exception thrown when FX rate is not available for a currency pair
 */
public class FxRateNotAvailableException extends FxAnalyzerException {

    private final String currencyPair;
    private final LocalDate rateDate;
    
    public FxRateNotAvailableException(String currencyPair, LocalDate rateDate) {
        super("FXAN-2002", String.format("FX rate not available for %s on %s", currencyPair, rateDate));
        this.currencyPair = currencyPair;
        this.rateDate = rateDate;
    }
    
    public FxRateNotAvailableException(String baseCurrency, String quoteCurrency, LocalDate rateDate) {
        this(baseCurrency + quoteCurrency, rateDate);
    }
    
    public String getCurrencyPair() {
        return currencyPair;
    }
    
    public LocalDate getRateDate() {
        return rateDate;
    }
}
