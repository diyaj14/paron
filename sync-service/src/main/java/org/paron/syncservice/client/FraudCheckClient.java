package org.paron.syncservice.client;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.FraudCheckResult;
import org.paron.syncservice.model.OfflineTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class FraudCheckClient {

    private final RestTemplate restTemplate;

    @Value("${services.fraud.url}")
    private String fraudServiceUrl;

    @Retry(name = "fraudService")
    public FraudCheckResult check(OfflineTransaction transaction) {
        String url = fraudServiceUrl + "/api/v1/fraud/check";

        Map<String, Object> body = Map.of(
                "userId", transaction.getUserId(),
                "deviceTransactionId", transaction.getDeviceTransactionId(),
                "offlineToken", transaction.getOfflineToken(),
                "amount", transaction.getAmount(),
                "merchantId", transaction.getMerchantId() != null ? transaction.getMerchantId() : "",
                "transactedAt", transaction.getTransactedAt() != null ? transaction.getTransactedAt().toString() : "",
                "deviceId", "",
                "tokenExpiryTime", ""
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling fraud-service for transaction. deviceTransactionId={}, userId={}",
                transaction.getDeviceTransactionId(), transaction.getUserId());

        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null) {
            log.warn("fraud-service returned null, defaulting to APPROVE");
            return FraudCheckResult.builder()
                    .score(0.0)
                    .approved(true)
                    .reason(null)
                    .build();
        }

        double score = ((Number) response.get("score")).doubleValue();
        String decision = (String) response.get("decision");
        String reason = (String) response.get("reason");

        return FraudCheckResult.builder()
                .score(score)
                .approved("APPROVE".equals(decision))
                .reason(reason)
                .build();
    }
}
