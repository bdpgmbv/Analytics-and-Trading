package com.vyshali.common.repository;

import com.vyshali.fxanalyzer.common.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdentifierTypeAndIdentifier(String identifierType, String identifier);
    
    Optional<Product> findByTicker(String ticker);
    
    List<Product> findByAssetClass(String assetClass);
    
    List<Product> findByIssueCurrency(String issueCurrency);
    
    List<Product> findByRiskRegion(String riskRegion);
    
    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    List<Product> findAllActive();
    
    @Query("SELECT p FROM Product p WHERE p.assetClass = :assetClass AND p.isActive = true")
    List<Product> findActiveByAssetClass(@Param("assetClass") String assetClass);
    
    @Query("SELECT p FROM Product p WHERE p.issueCurrency = :currency AND p.isActive = true")
    List<Product> findActiveByIssueCurrency(@Param("currency") String currency);
    
    @Query("SELECT DISTINCT p.issueCurrency FROM Product p WHERE p.isActive = true")
    List<String> findDistinctIssueCurrencies();
    
    @Query("SELECT DISTINCT p.assetClass FROM Product p WHERE p.isActive = true")
    List<String> findDistinctAssetClasses();
    
    @Query("SELECT p FROM Product p WHERE p.identifier LIKE %:search% OR p.ticker LIKE %:search% OR p.securityDescription LIKE %:search%")
    List<Product> searchProducts(@Param("search") String search);
    
    boolean existsByIdentifierTypeAndIdentifier(String identifierType, String identifier);
}
