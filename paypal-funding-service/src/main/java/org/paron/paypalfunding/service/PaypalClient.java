package org.paron.paypalfunding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Thin server-side Orders v2 client. Credentials are never sent to the browser. */
@Component
public class PaypalClient {
    private static final Logger log = LoggerFactory.getLogger(PaypalClient.class);
    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    public PaypalClient(@Value("${paypal.base-url}") String baseUrl,
                        @Value("${paypal.client-id}") String clientId,
                        @Value("${paypal.client-secret}") String clientSecret) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
    }
    public JsonNode createOrder(BigDecimal amount, String currency) {
        try {
            String response = client.post().uri("/v2/checkout/orders").headers(this::bearer).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("intent", "CAPTURE", "purchase_units", new Object[]{Map.of("amount", Map.of("currency_code", currency, "value", amount.setScale(2).toPlainString()))},
                            "application_context", Map.of("return_url", "paron://paypal-return", "cancel_url", "paron://paypal-cancel")))
                    .retrieve().body(String.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            logPayPalFailure("create order", exception);
            throw exception;
        }
    }
    public JsonNode capture(String orderId) {
        try {
            String response = client.post().uri("/v2/checkout/orders/{orderId}/capture", orderId).headers(this::bearer)
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().body(String.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            logPayPalFailure("capture order", exception);
            throw exception;
        }
    }
    /** Used before a capture retry: a remote capture can succeed even if our local request timed out. */
    public JsonNode getOrder(String orderId) {
        try {
            String response = client.get().uri("/v2/checkout/orders/{orderId}", orderId).headers(this::bearer)
                    .retrieve().body(String.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            logPayPalFailure("fetch order", exception);
            throw exception;
        }
    }
    private void bearer(HttpHeaders headers) { headers.setBearerAuth(accessToken()); }
    private String accessToken() {
        String response;
        try {
            response = client.post().uri("/v1/oauth2/token")
                    .headers(h -> h.setBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body("grant_type=client_credentials")
                    .retrieve().body(String.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            logPayPalFailure("OAuth token request", exception);
            throw new PaypalCredentialsException(
                    "PayPal rejected the configured API credentials. Check that PAYPAL_CLIENT_ID and " +
                    "PAYPAL_CLIENT_SECRET are the matching credentials from one PayPal Sandbox REST app, " +
                    "and that PAYPAL_BASE_URL is https://api-m.sandbox.paypal.com.");
        } catch (RestClientResponseException exception) {
            logPayPalFailure("OAuth token request", exception);
            throw exception;
        }
        JsonNode token = parse(response);
        if (token == null || token.path("access_token").asText().isBlank()) throw new IllegalStateException("PayPal did not return an access token");
        return token.path("access_token").asText();
    }
    private JsonNode parse(String response) {
        try { return objectMapper.readTree(response); }
        catch (Exception exception) { throw new IllegalStateException("Could not parse PayPal response", exception); }
    }

    private void logPayPalFailure(String operation, RestClientResponseException exception) {
        String error = "unknown";
        String description = "PayPal returned an error without a description.";
        String debugId = "unavailable";
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            error = firstText(body, "error", "name", "issue");
            description = firstText(body, "error_description", "message");
            debugId = firstText(body, "debug_id");
        } catch (Exception ignored) {
            // Keep logs useful without dumping an unstructured response body.
        }
        if (description.length() > 300) description = description.substring(0, 300);
        log.error("PayPal {} failed: HTTP {}, code={}, description={}, debugId={}",
                operation, exception.getStatusCode().value(), error, description, debugId);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText();
            if (!value.isBlank()) return value;
        }
        return "unavailable";
    }
}
