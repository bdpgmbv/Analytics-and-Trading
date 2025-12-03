package com.vyshali.priceservice.service;

/*
 * 12/02/2025 - 6:45 PM
 * @author Vyshali Prabananth Lal
 */

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PositionCacheService {

    // AccountID -> ProductID -> Quantity
    private final Map<Integer, Map<Integer, BigDecimal>> accountPositions = new ConcurrentHashMap<>();

    // Reverse Index: ProductID -> Set<AccountID>
    // Critical for O(1) lookup of affected accounts during a price tick
    private final Map<Integer, Set<Integer>> productToAccounts = new ConcurrentHashMap<>();

    public void updatePosition(Integer accountId, Integer productId, BigDecimal quantity) {
        // Update Forward Map
        accountPositions.computeIfAbsent(accountId, k -> new ConcurrentHashMap<>()).put(productId, quantity);

        // Update Reverse Index
        productToAccounts.computeIfAbsent(productId, k -> ConcurrentHashMap.newKeySet()).add(accountId);
    }

    public BigDecimal getQuantity(Integer accountId, Integer productId) {
        return accountPositions.getOrDefault(accountId, Map.of()).getOrDefault(productId, BigDecimal.ZERO);
    }

    public Set<Integer> getAccountsHoldingProduct(Integer productId) {
        return productToAccounts.getOrDefault(productId, Set.of());
    }
}
