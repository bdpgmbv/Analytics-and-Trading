package com.vyshali.mockupstream.service;

/*
 * 12/02/2025 - 2:03 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaPublisherService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 1. MSPM EOD Trigger
    public void sendEodTrigger(Integer accountId) {
        kafkaTemplate.send("MSPM_EOD_TRIGGER", accountId.toString());
        log.info("Sent EOD Trigger for {}", accountId);
    }

    // 2. MSPA Intraday Updates
    public void sendIntradayUpdate(AccountSnapshotDTO payload) {
        kafkaTemplate.send("MSPA_INTRADAY", payload.accountId().toString(), payload);
    }

    // 3. Market Data (Prices)
    public void sendPriceTick(PriceTickDTO tick) {
        kafkaTemplate.send("MARKET_DATA_TICKS", tick.productId().toString(), tick);
    }

    // 4. FX Rates
    public void sendFxRate(FxRateDTO rate) {
        kafkaTemplate.send("FX_RATES_TICKS", rate.currencyPair(), rate);
    }

    // 5. Execution Reports (THIS IS THE MISSING METHOD)
    public void sendExecutionReport(ExecutionReportDTO report) {
        // We use the OrderID as the key to ensure order updates are sequential
        kafkaTemplate.send("RAW_EXECUTION_REPORTS", report.orderId(), report);
        log.info("Sent Execution Report: {} for Order: {}", report.execId(), report.orderId());
    }
}