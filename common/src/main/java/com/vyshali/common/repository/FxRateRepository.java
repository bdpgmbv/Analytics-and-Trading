package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findByCurrencyPairAndRateDateAndSource(
            String currencyPair, LocalDate rateDate, String source);
    
    List<FxRate> findByCurrencyPairAndRateDate(String currencyPair, LocalDate rateDate);
    
    List<FxRate> findByRateDate(LocalDate rateDate);
    
    @Query("SELECT f FROM FxRate f WHERE f.baseCurrency = :baseCurrency AND f.quoteCurrency = :quoteCurrency " +
           "AND f.rateDate = :rateDate ORDER BY f.source")
    List<FxRate> findByBaseCurrencyAndQuoteCurrencyAndDate(
            @Param("baseCurrency") String baseCurrency,
            @Param("quoteCurrency") String quoteCurrency,
            @Param("rateDate") LocalDate rateDate);
    
    /**
     * Get the latest rate for a currency pair
     */
    @Query("SELECT f FROM FxRate f WHERE f.currencyPair = :currencyPair " +
           "ORDER BY f.rateDate DESC, f.rateTime DESC")
    List<FxRate> findLatestByCurrencyPair(@Param("currencyPair") String currencyPair);
    
    default Optional<FxRate> findLatestRate(String currencyPair) {
        return findLatestByCurrencyPair(currencyPair).stream().findFirst();
    }
    
    @Query("SELECT f FROM FxRate f WHERE f.rateDate = :rateDate AND f.isStale = false")
    List<FxRate> findActiveRatesByDate(@Param("rateDate") LocalDate rateDate);
    
    @Query("SELECT DISTINCT f.currencyPair FROM FxRate f WHERE f.rateDate = :rateDate")
    List<String> findDistinctCurrencyPairsOnDate(@Param("rateDate") LocalDate rateDate);
    
    @Query("SELECT f FROM FxRate f WHERE f.currencyPair IN :pairs AND f.rateDate = :rateDate")
    List<FxRate> findByPairsAndDate(
            @Param("pairs") List<String> currencyPairs,
            @Param("rateDate") LocalDate rateDate);
    
    /**
     * Find rate for conversion (handles direct and inverted pairs)
     */
    @Query("SELECT f FROM FxRate f WHERE " +
           "((f.baseCurrency = :fromCurrency AND f.quoteCurrency = :toCurrency) OR " +
           "(f.baseCurrency = :toCurrency AND f.quoteCurrency = :fromCurrency)) " +
           "AND f.rateDate = :rateDate")
    List<FxRate> findConversionRate(
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency,
            @Param("rateDate") LocalDate rateDate);
}
