package com.vyshali.mockupstream.service;

/*
 * 12/02/2025 - 2:03 PM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.mockupstream.dto.AccountSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaPublisherService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEodTrigger(Integer accountId) {
        // Send String ID for EOD
        kafkaTemplate.send("MSPM_EOD_TRIGGER", accountId.toString());
    }

    public void sendIntradayUpdate(AccountSnapshotDTO payload) {
        // Send JSON payload for Intraday
        kafkaTemplate.send("MSPA_INTRADAY", payload.accountId().toString(), payload);
    }
}
