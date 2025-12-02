package com.vyshali.mockupstream.controller;

/*
 * 12/02/2025 - 2:04 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.service.DataGeneratorService;
import com.vyshali.mockupstream.service.KafkaPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final DataGeneratorService dataGen;
    private final KafkaPublisherService kafka;

    @PostMapping("/eod/{accountId}")
    public String simulateEod(@PathVariable Integer accountId, @RequestParam(defaultValue = "MEDIUM") DataGeneratorService.FundSize size) {
        dataGen.registerAccountSize(accountId, size);
        kafka.sendEodTrigger(accountId);
        return "EOD Trigger sent for " + accountId + " (" + size + ")";
    }

    @PostMapping("/intraday/stream/{accountId}")
    public String streamIntraday(@PathVariable Integer accountId, @RequestParam(defaultValue = "10000") int count) {
        CompletableFuture.runAsync(() -> {
            dataGen.streamIntradayTrades(accountId, count, kafka::sendIntradayUpdate);
        });
        return "Streaming " + count + " trades to Kafka...";
    }
}
