package com.vyshali.mockupstream.service;

/*
 * 12/02/2025 - 2:02 PM
 * @author Vyshali Prabananth Lal
 */

import com.github.javafaker.Faker;
import com.vyshali.mockupstream.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@Service
public class DataGeneratorService {

    private final Faker faker = new Faker();
    private final Random random = new Random();

    // Stores the size for EOD retrieval
    private final Map<Integer, Integer> accountSizeMap = new ConcurrentHashMap<>();

    public enum FundSize {
        SMALL(100), MEDIUM(5_000), LARGE(50_000), WHALE(1_000_000); // 1 Million Rows

        public final int count;

        FundSize(int count) {
            this.count = count;
        }
    }

    public void registerAccountSize(Integer accountId, FundSize size) {
        accountSizeMap.put(accountId, size.count);
    }

    // EOD: Generate huge list on demand
    public AccountSnapshotDTO generateEodSnapshot(Integer accountId) {
        int count = accountSizeMap.getOrDefault(accountId, 100);
        log.info("Generating {} positions for Account {}", count, accountId);

        List<PositionDetailDTO> positions = IntStream.range(0, count).parallel() // Use all CPU cores
                .mapToObj(i -> generatePosition(i)).toList();

        return new AccountSnapshotDTO(100, "Mock Client", 200, "Mock Fund", "USD", accountId, "ACC-" + accountId, "CUSTODY", positions);
    }

    // INTRADAY: Stream batches to callback
    public void streamIntradayTrades(Integer accountId, int totalTrades, java.util.function.Consumer<AccountSnapshotDTO> publisher) {
        log.info("Streaming {} trades for Account {}", totalTrades, accountId);
        int batchSize = 500;
        AtomicInteger counter = new AtomicInteger(0);

        IntStream.range(0, (totalTrades + batchSize - 1) / batchSize).forEach(batchIdx -> {
            List<PositionDetailDTO> batch = new ArrayList<>();
            for (int i = 0; i < batchSize && counter.get() < totalTrades; i++) {
                batch.add(generateRandomTrade());
                counter.incrementAndGet();
            }

            AccountSnapshotDTO payload = new AccountSnapshotDTO(100, "Mock Client", 200, "Mock Fund", "USD", accountId, "ACC-" + accountId, "CUSTODY", batch);

            publisher.accept(payload); // Push to Kafka

            // Throttle slightly to prevent local Kafka buffer overflow
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
        });

        log.info("Finished streaming {} trades.", totalTrades);
    }

    private PositionDetailDTO generatePosition(int seed) {
        return new PositionDetailDTO(1000000 + seed, "TICKER_" + seed, "EQUITY", "USD", BigDecimal.valueOf(Math.abs(random.nextGaussian() * 1000)).setScale(2, RoundingMode.HALF_UP), "EOD_HOLDING");
    }

    private PositionDetailDTO generateRandomTrade() {
        return new PositionDetailDTO(1000000 + random.nextInt(10000), // Random existing product
                "TICKER_X", "EQUITY", "USD", BigDecimal.valueOf(Math.abs(random.nextGaussian() * 100)).setScale(2, RoundingMode.HALF_UP), random.nextBoolean() ? "BUY" : "SELL");
    }
}
