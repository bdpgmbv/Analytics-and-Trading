package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.PriceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceOverrideRepository extends JpaRepository<PriceOverride, Long> {

    @Query("SELECT o FROM PriceOverride o WHERE o.product.productId = :productId " +
           "AND o.isActive = true AND o.effectiveDate <= :date " +
           "AND (o.expiryDate IS NULL OR o.expiryDate >= :date)")
    Optional<PriceOverride> findActiveOverrideForProduct(
            @Param("productId") Long productId,
            @Param("date") LocalDate date);
    
    @Query("SELECT o FROM PriceOverride o WHERE o.currencyPair = :currencyPair " +
           "AND o.isActive = true AND o.effectiveDate <= :date " +
           "AND (o.expiryDate IS NULL OR o.expiryDate >= :date)")
    Optional<PriceOverride> findActiveOverrideForCurrencyPair(
            @Param("currencyPair") String currencyPair,
            @Param("date") LocalDate date);
    
    @Query("SELECT o FROM PriceOverride o WHERE o.isActive = true AND o.effectiveDate <= :date " +
           "AND (o.expiryDate IS NULL OR o.expiryDate >= :date)")
    List<PriceOverride> findAllActiveOverrides(@Param("date") LocalDate date);
    
    List<PriceOverride> findByAccountAccountId(Long accountId);
    
    @Query("SELECT o FROM PriceOverride o WHERE o.createdBy = :username AND o.isActive = true")
    List<PriceOverride> findActiveByCreatedBy(@Param("username") String username);
}
