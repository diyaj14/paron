package org.paron.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
/*
 * HTTP client for communicating with the ledger-service.
 *
 * Why is this a separate class?
 * The TokenService should not know HOW to make HTTP calls.
 * It just says "reserve funds for this user" and this class handles
 * the actual HTTP request, URL construction, error handling, etc.
 * This is called the Single Responsibility Principle.
 *
 * In production you would use:
 *   - Spring Cloud OpenFeign (declarative HTTP client) — much cleaner
 *   - Or WebClient (reactive, non-blocking)
 * For simplicity we use RestTemplate here which you already know.
 */

@Component
@Slf4j
public class LedgerServiceClient {
    private final RestTemplate restTemplate;
    private final String ledgerServiceUrl;
/*When you create this object,
 * go open the settings file,
* find the value at services.ledger.url,
and plug that value in right here,@allargsconstruotr doesnt help to use @value annotaiton
*/
    public LedgerServiceClient(RestTemplate restTemplate,@Value("${services.ledger.url}") String ledgerServiceUrl) {
        this.restTemplate = restTemplate;
        this.ledgerServiceUrl = ledgerServiceUrl;
    }
    /*
     * Calls POST /api/v1/ledger/reserve on ledger-service.
     * Returns the reservationId that the ledger assigned.
     *
     * If ledger-service returns an error (e.g. insufficient funds),
     * RestTemplate throws an exception, which propagates up to
     * TokenService and causes the whole transaction to roll back.
     */
    public String reserveFunds(String userId,BigDecimal amount){
        String url = ledgerServiceUrl + "/api/v1/ledger/reserve";
        Map<String,Object> body = Map.of(
                "userId",userId,
                "amount",amount
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,Object>> requestEntity = new HttpEntity<>(body,headers);

        log.info("Calling ledger-service to reserve funds. userId={}, amount={}", userId, amount);
        /*You're assigning a plain Map (returned by postForObject) into a variable declared as Map<String, String>. The compiler can't actually confirm that what comes back truly
         has String keys and String values — it's trusting you. (Java cant identify
         type of map at runtime
         */
        @SuppressWarnings("unchecked")
        Map<String,String> response = restTemplate.postForObject(url,requestEntity,Map.class);
        if (response == null || !response.containsKey("reservationId")) {
            throw new RuntimeException("Invalid response from ledger-service during reserve");
        }
        return response.get("reservationId");
    }
   /* Calls POST /api/v1/ledger/release on ledger-service.
     * Used when a token expires — unlocks the reserved funds.
     */
    public void releaseReservation(String reservationId){
        String url = ledgerServiceUrl + "/api/v1/ledger/release";
        Map <String,String> body = Map.of("reservationId",reservationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,String>> requestEntity = new HttpEntity<>(body,headers);
        log.info("Calling ledger-service to release reservation. reservationId={}", reservationId);

        restTemplate.postForObject(url, requestEntity, Void.class);

    }


}
