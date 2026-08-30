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

import java.util.List;
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
                "deviceId", transaction.getDeviceId() != null ? transaction.getDeviceId() : "",
                "tokenExpiryTime", ""
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling fraud-service for transaction. deviceTransactionId={}, userId={}",
                transaction.getDeviceTransactionId(), transaction.getUserId());

        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);

        if (response == null) {
            log.warn("fraud-service returned null, defaulting to HOLD_FOR_REVIEW (fail-closed)");
            return FraudCheckResult.builder()
                    .score(0.0)
                    .approved(false)
                    .reason("model_unavailable")
                    .build();
        }

        double score = ((Number) response.get("score")).doubleValue();
        String decision = (String) response.get("decision");
        String reason = (String) response.get("reason");
        Double confidence = response.get("confidence") != null ? ((Number) response.get("confidence")).doubleValue() : null;
        String modelVersion = (String) response.get("modelVersion");
        String policyVersion = (String) response.get("policyVersion");

        @SuppressWarnings("unchecked")
        List<String> reasonCodes = response.get("reasonCodes") != null
                ? (List<String>) response.get("reasonCodes") : List.of();

        return FraudCheckResult.builder()
                .score(score)
                .approved("APPROVE".equals(decision))
                .decision(decision)
                .reason(reason)
                .confidence(confidence)
                .modelVersion(modelVersion)
                .policyVersion(policyVersion)
                .reasonCodes(reasonCodes)
                .build();
    }
}
