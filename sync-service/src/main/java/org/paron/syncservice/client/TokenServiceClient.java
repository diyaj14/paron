package org.paron.syncservice.client;


import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.TokenValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import org.springframework.http.HttpHeaders;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.token.url}")
    private String tokenServiceUrl;

    @Retry(name="tokenService")
    public TokenValidationResult validateToken(String token, BigDecimal spentAmount){
        String url = tokenServiceUrl + "/api/v1/tokens/validate";

        Map<String,Object> body = Map.of(
                "token",token,
                "spentAmount",spentAmount
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String,Object>> requestEntity = new HttpEntity<>(body,headers);

        log.info("Validating token with token-service. spentAmount={}", spentAmount);
        return restTemplate.postForObject(url, requestEntity, TokenValidationResult.class);
    }
    @Retry(name = "tokenService")
    public void markAsUsed(String token, BigDecimal finalSpentAmount) {
        String url = tokenServiceUrl + "/api/v1/tokens/mark-used";

        Map<String, Object> body = Map.of(
                "token", token,
                "finalSpentAmount", finalSpentAmount
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        log.info("Marking token as used. finalSpentAmount={}", finalSpentAmount);

        restTemplate.postForObject(url, requestEntity, Void.class);
    }


}
