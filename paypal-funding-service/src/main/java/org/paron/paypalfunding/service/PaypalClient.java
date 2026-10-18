package org.paron.paypalfunding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

/** Thin server-side Orders v2 client. Credentials are never sent to the browser. */
@Component
public class PaypalClient {
    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    public PaypalClient(@Value("${paypal.base-url}") String baseUrl,
                        @Value("${paypal.client-id}") String clientId,
                        @Value("${paypal.client-secret}") String clientSecret) {
        this.client = RestClient.builder().baseUrl(baseUrl).build(); this.clientId = clientId; this.clientSecret = clientSecret;
    }
    public JsonNode createOrder(BigDecimal amount, String currency) {
        String response = client.post().uri("/v2/checkout/orders").headers(this::bearer).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("intent", "CAPTURE", "purchase_units", new Object[]{Map.of("amount", Map.of("currency_code", currency, "value", amount.setScale(2).toPlainString()))}))
                .retrieve().body(String.class);
        return parse(response);
    }
    public JsonNode capture(String orderId) {
        String response = client.post().uri("/v2/checkout/orders/{orderId}/capture", orderId).headers(this::bearer)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().body(String.class);
        return parse(response);
    }
    private void bearer(HttpHeaders headers) { headers.setBearerAuth(accessToken()); }
    private String accessToken() {
        String response = client.post().uri("/v1/oauth2/token").headers(h -> h.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials").retrieve().body(String.class);
        JsonNode token = parse(response);
        if (token == null || token.path("access_token").asText().isBlank()) throw new IllegalStateException("PayPal did not return an access token");
        return token.path("access_token").asText();
    }
    private JsonNode parse(String response) {
        try { return objectMapper.readTree(response); }
        catch (Exception exception) { throw new IllegalStateException("Could not parse PayPal response", exception); }
    }
}
