package org.paron.paypalfunding.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
@Component
public class LedgerClient {
    private final RestClient client;
    public LedgerClient(@Value("${ledger.base-url}") String baseUrl) { client = RestClient.builder().baseUrl(baseUrl).build(); }
    public void credit(UUID fundingId, String userId, BigDecimal amount) {
        client.post().uri("/api/v1/ledger/funding/credit").body(Map.of("fundingId", fundingId, "userId", userId, "amount", amount)).retrieve().toBodilessEntity();
    }
}
