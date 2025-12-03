package com.vyshali.mockupstream.controller;

/*
 * 12/02/2025 - 2:04 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/simulate")
@RequiredArgsConstructor
public class SimulationController {

    private final PositionGeneratorService posGen;
    private final KafkaPublisherService kafka;
    private final MarketDataGeneratorService marketGen;
    private final ExecutionGeneratorService execGen;

    @PostMapping("/eod/{accountId}")
    public String triggerEod(@PathVariable Integer accountId, @RequestParam(defaultValue = "50") int size) {
        posGen.generateEod(accountId, size);
        kafka.sendEodTrigger(accountId);
        return "EOD Triggered for " + accountId;
    }

    @PostMapping("/market-data/start")
    public String startMarketData(@RequestParam(defaultValue = "60") int seconds) {
        marketGen.startStreaming(seconds);
        return "Streaming prices for " + seconds + "s";
    }

    @PostMapping("/trade/fill")
    public String triggerFill(@RequestParam Integer accountId, @RequestParam String ticker, @RequestParam int qty) {
        execGen.simulateTrade(accountId, ticker, qty, 150.00);
        return "Simulating execution for " + ticker;
    }
}