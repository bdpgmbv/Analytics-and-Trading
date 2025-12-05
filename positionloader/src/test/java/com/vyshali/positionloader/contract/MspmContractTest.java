package com.vyshali.positionloader.contract;

/*
 * 12/05/2025 - 11:13 AM
 * @author Vyshali Prabananth Lal
 */

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.vyshali.positionloader.dto.AccountSnapshotDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

import java.util.Map;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "MSPM_Upstream_Service")
public class MspmContractTest {

    @Pact(consumer = "PositionLoader_Service")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        return builder.given("Account 1001 exists").uponReceiving("A request for EOD Snapshot").path("/mspm/accounts/1001/eod-snapshot").method("GET").headers(Map.of("App-ID", "FXAN-EOD")) // Enforce we send headers
                .willRespondWith().status(200).body("""
                            {
                                "accountId": 1001,
                                "clientName": "Apex Capital",
                                "baseCurrency": "USD",
                                "positions": [
                                    {
                                        "ticker": "AAPL",
                                        "quantity": 100,
                                        "externalRefId": "REF-123" 
                                    }
                                ]
                            }
                        """) // We strictly expect 'externalRefId' to be present
                .toPact();
    }

    @Test
    void testEodFetch(MockServer mockServer) {
        // 1. Point our Client to the Pact Mock Server
        RestClient client = RestClient.builder().baseUrl(mockServer.getUrl()).defaultHeader("App-ID", "FXAN-EOD").build();

        // 2. Execute the actual HTTP call
        AccountSnapshotDTO result = client.get().uri("/mspm/accounts/1001/eod-snapshot").retrieve().body(AccountSnapshotDTO.class);

        // 3. Verify our code can parse the response
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1001, result.accountId());
        Assertions.assertEquals("REF-123", result.positions().get(0).externalRefId());
    }
}
