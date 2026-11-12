package org.paron.paypalfunding.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
@Component
public class LedgerClient {
    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);
    private final RestClient client;
    private final String baseUrl;

    public LedgerClient(@Value("${ledger.base-url}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.client = RestClient.builder().baseUrl(this.baseUrl).build();
        log.info("Ledger credit client configured. baseUrl={}", this.baseUrl);
    }

    public void credit(UUID fundingId, String userId, BigDecimal amount) {
        try {
            client.post().uri("/api/v1/ledger/funding/credit")
                    .body(Map.of("fundingId", fundingId, "userId", userId, "amount", amount))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            log.error("Ledger rejected PayPal credit. fundingId={}, httpStatus={}",
                    fundingId, exception.getStatusCode().value());
            throw LedgerCreditPendingException.rejected(exception.getStatusCode().value(), exception);
        } catch (ResourceAccessException exception) {
            log.error("Ledger could not be reached for PayPal credit. fundingId={}, baseUrl={}",
                    fundingId, baseUrl, exception);
            throw LedgerCreditPendingException.unreachable(exception);
        }
    }
}
