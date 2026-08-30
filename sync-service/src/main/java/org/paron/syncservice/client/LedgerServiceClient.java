package org.paron.syncservice.client;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import org.springframework.http.HttpHeaders;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class LedgerServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.ledger.url}")
    private String ledgerServiceUrl;

    @CircuitBreaker(name="ledgerService",fallbackMethod = "settleFallback")
    @Retry(name="ledgerService")
    public Map<String,Object> settle(String reservationId, BigDecimal spentAmount){
        String url = ledgerServiceUrl +"/api/v1/ledger/settle";

        Map<String,Object> body = Map.of(
                "reservationId",reservationId,
                "spentAmount",spentAmount
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,Object>> requestEntity =new HttpEntity<>(body,headers);

        log.info("Calling ledger-service to settle. reservationId={}, spentAmount={}",
                reservationId, spentAmount);

        @SuppressWarnings("unchecked")
        Map<String,Object> response = restTemplate.postForObject(url,requestEntity,Map.class);
        return response;
    }
    /*
     * Fallback method — Resilience4j calls this automatically whenever the
     * circuit is OPEN, or when settle() throws after exhausting retries.
     * The method signature must match settle()'s, plus one extra parameter
     * for the exception that triggered the fallback.
     *
     * We don't try to fake a successful response here — we let the caller
     * (SettlementProcessor) know settlement genuinely failed, so it can
     * mark the transaction FAILED and leave it eligible for the batch job
     * to retry on its next scheduled run.
     */
    public Map<String, Object> settleFallback(String reservationId, BigDecimal spentAmount, Throwable t) {
        log.error("Ledger-service settlement fallback triggered. reservationId={}, reason={}",
                reservationId, t.getMessage());
        throw new RuntimeException("ledger-service unavailable, settlement deferred for retry", t);
    }

    /*
     * Best-effort merchant credit — called by SettlementWriter after the
     * customer side of a payment has settled. Never fails the transaction:
     * callers catch and log, since the money is already moving correctly.
     */
    public void creditMerchant(String merchantId, BigDecimal amount) {
        String url = ledgerServiceUrl + "/api/v1/ledger/merchants/" + merchantId + "/credit";
        Map<String,Object> body = Map.of("amount", amount);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,Object>> requestEntity = new HttpEntity<>(body, headers);
        log.info("Calling ledger-service to credit merchant. merchantId={}, amount={}",
                merchantId, amount);
        restTemplate.postForObject(url, requestEntity, Map.class);
    }



}
