package org.paron.service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;import lombok.extern.slf4j.Slf4j;
import org.paron.exception.TokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
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
 * For simplicity we use RestTemplate here
 */

@Component
@Slf4j
public class LedgerServiceClient {
    private final RestTemplate restTemplate;
    private final String ledgerServiceUrl;
/*When you create this object,
 * go open the settings file,
* find the value at services.ledger.url,
and plug that value in right here,
* @allargsconstruotr doesn't help to use @value annotaiton
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
        Map<String,String> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String,String> raw = restTemplate.postForObject(url,requestEntity,Map.class);
            response = raw;
        } catch (HttpStatusCodeException e) {
            // Ledger returned an error (e.g. INSUFFICIENT_FUNDS). Surface it as a
            // proper business error instead of letting it bubble up as a 500.
            String errorCode = "LEDGER_ERROR";
            String message = "Ledger-service rejected the reservation (HTTP " + e.getStatusCode().value() + ")";
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> parsedBody = mapper.readValue(
                        e.getResponseBodyAsString(),
                        new TypeReference<Map<String, Object>>() {});
                if (parsedBody != null) {
                    if (parsedBody.get("errorCode") != null) {
                        errorCode = String.valueOf(parsedBody.get("errorCode"));
                    }
                    if (parsedBody.get("message") != null) {
                        message = String.valueOf(parsedBody.get("message"));
                    }
                }
            } catch (Exception ignored) {
                // Response body wasn't JSON; fall back to the defaults above
            }
            throw new TokenException(errorCode, message);
        }
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

    /*
     * Calls POST /api/v1/ledger/settle on ledger-service.
     * Used when a token is marked as USED — debits the actual spent amount
     * and releases any remaining reserved funds back to available balance.
     */
    public void settleReservation(String reservationId, BigDecimal spentAmount) {
        String url = ledgerServiceUrl + "/api/v1/ledger/settle";
        Map<String, Object> body = Map.of(
                "reservationId", reservationId,
                "spentAmount", spentAmount
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        log.info("Calling ledger-service to settle reservation. reservationId={}, spentAmount={}",
                reservationId, spentAmount);

        try {
            restTemplate.postForObject(url, requestEntity, Void.class);
        } catch (HttpStatusCodeException e) {
            // Ledger rejected the settlement (e.g. RESERVATION_ALREADY_CLOSED on replay).
            // Surface it as a proper business error instead of a raw 500.
            String errorCode = "LEDGER_ERROR";
            String message = "Ledger-service rejected the settlement (HTTP " + e.getStatusCode().value() + ")";
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> parsedBody = mapper.readValue(
                        e.getResponseBodyAsString(),
                        new TypeReference<Map<String, Object>>() {});
                if (parsedBody != null) {
                    if (parsedBody.get("errorCode") != null) {
                        errorCode = String.valueOf(parsedBody.get("errorCode"));
                    }
                    if (parsedBody.get("message") != null) {
                        message = String.valueOf(parsedBody.get("message"));
                    }
                }
            } catch (Exception ignored) {
                // Response body wasn't JSON; fall back to the defaults above
            }
            throw new TokenException(errorCode, message);
        }
    }


}
