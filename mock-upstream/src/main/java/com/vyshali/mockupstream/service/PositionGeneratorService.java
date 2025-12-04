package com.vyshali.mockupstream.service;

/*
 * 12/03/2025 - 6:07 PM
 * @author Vyshali Prabananth Lal
 */

import com.github.javafaker.Faker;
import com.vyshali.mockupstream.dto.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

@Service
public class PositionGeneratorService {
    private final Faker faker = new Faker();
    private final Random random = new Random();
    private final Map<Integer, AccountSnapshotDTO> eodCache = new ConcurrentHashMap<>();

    public AccountSnapshotDTO generateEod(Integer accountId, int size) {
        // Generate a list of random positions
        List<PositionDetailDTO> positions = IntStream.range(0, size).mapToObj(i -> new PositionDetailDTO(1000 + i,                       // 1. productId
                "TICKER_" + (1000 + i),           // 2. ticker
                "EQUITY",                       // 3. assetClass
                "USD",                          // 4. issueCurrency
                BigDecimal.valueOf(random.nextInt(1000)), // 5. quantity
                "EOD_HOLDING",                  // 6. txnType
                BigDecimal.valueOf(100 + random.nextInt(50)) // 7. price
        )).toList();

        // Create the snapshot wrapper
        AccountSnapshotDTO snapshot = new AccountSnapshotDTO(100, "Mock Client", 200, "Mock Fund", "USD", accountId, "ACC-" + accountId, "CUSTODY", positions);

        eodCache.put(accountId, snapshot);
        return snapshot;
    }

    public AccountSnapshotDTO getCachedEod(Integer accountId) {
        return eodCache.get(accountId);
    }
}