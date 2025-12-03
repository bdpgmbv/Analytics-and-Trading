package com.vyshali.mockupstream.service;

/*
 * 12/03/2025 - 5:24 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.dto.FxRateDTO;
import com.vyshali.mockupstream.dto.PriceTickDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataGeneratorService {

    private final KafkaPublisherService publisher;
    private final Random random = new Random();
    private volatile boolean running = false;

    @Async
    public void startStreaming(int durationSeconds) {
        running = true;
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        log.info("Starting Price Stream for {} seconds...", durationSeconds);

        double[] prices = new double[100];
        for (int i = 0; i < 100; i++) prices[i] = 100.0;

        while (running && System.currentTimeMillis() < endTime) {
            // Generate Equity Ticks
            for (int k = 0; k < 5; k++) {
                int id = random.nextInt(100);
                prices[id] += (random.nextDouble() - 0.5);
                PriceTickDTO tick = new PriceTickDTO(1000 + id, "TICKER_" + (1000 + id), BigDecimal.valueOf(prices[id]), "USD", Instant.now(), "FILTER_REALTIME");
                publisher.sendPriceTick(tick);
            }
            // Generate FX Rates
            FxRateDTO fx = new FxRateDTO("EURUSD", BigDecimal.valueOf(1.08 + (random.nextDouble() * 0.001)), BigDecimal.ZERO, Instant.now(), "FILTER_FX");
            publisher.sendFxRate(fx);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        log.info("Price Stream Stopped.");
    }
}