package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, Long> {

    /**
     * Find the best available price for a product using the price hierarchy.
     * Priority: OVERRIDE (1) > REALTIME (2) > RCP_SNAP (3) > MSPA (4)
     */
    @Query("SELECT p FROM Price p WHERE p.product.productId = :productId AND p.priceDate = :priceDate " +
           "ORDER BY p.sourcePriority ASC")
    List<Price> findPricesByProductAndDateOrderByPriority(
            @Param("productId") Long productId,
            @Param("priceDate") LocalDate priceDate);
    
    /**
     * Get the single best price (first in hierarchy order)
     */
    default Optional<Price> findBestPrice(Long productId, LocalDate priceDate) {
        return findPricesByProductAndDateOrderByPriority(productId, priceDate).stream().findFirst();
    }
    
    List<Price> findByProductProductIdAndPriceDate(Long productId, LocalDate priceDate);
    
    List<Price> findByPriceDateAndSource(LocalDate priceDate, String source);
    
    @Query("SELECT p FROM Price p WHERE p.priceDate = :priceDate AND p.isStale = true")
    List<Price> findStalePrices(@Param("priceDate") LocalDate priceDate);
    
    @Query("SELECT p FROM Price p WHERE p.product.productId IN :productIds AND p.priceDate = :priceDate " +
           "AND p.sourcePriority = (SELECT MIN(p2.sourcePriority) FROM Price p2 " +
           "WHERE p2.product.productId = p.product.productId AND p2.priceDate = :priceDate)")
    List<Price> findBestPricesForProducts(
            @Param("productIds") List<Long> productIds,
            @Param("priceDate") LocalDate priceDate);
    
    @Modifying
    @Query("UPDATE Price p SET p.isStale = true WHERE p.priceDate < :cutoffDate AND p.source = 'REALTIME'")
    int markOldRealtimePricesAsStale(@Param("cutoffDate") LocalDate cutoffDate);
    
    @Modifying
    @Query("DELETE FROM Price p WHERE p.priceDate < :cutoffDate")
    int deleteOldPrices(@Param("cutoffDate") LocalDate cutoffDate);
    
    @Query("SELECT DISTINCT p.product.productId FROM Price p WHERE p.priceDate = :priceDate")
    List<Long> findProductIdsWithPricesOnDate(@Param("priceDate") LocalDate priceDate);
}
