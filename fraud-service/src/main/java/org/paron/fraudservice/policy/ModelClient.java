package org.paron.fraudservice.policy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ModelClient {

    private final RestTemplate restTemplate;
    private final String modelServiceUrl;

    public ModelClient(
            RestTemplate restTemplate,
            @Value("${model.service.url:http://localhost:8600}") String modelServiceUrl) {
        this.restTemplate = restTemplate;
        this.modelServiceUrl = modelServiceUrl;
    }

    public ModelOutput score(Map<String, Double> features, String correlationId) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("correlationId", correlationId);
            request.put("featureSchemaVersion", "v1");
            request.put("features", features);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    modelServiceUrl + "/v1/score",
                    entity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List<Map<String, Object>> contributions = (List<Map<String, Object>>) body.get("topContributions");
                List<ModelOutput.Contribution> contribList = contributions != null ?
                        contributions.stream().map(c -> ModelOutput.Contribution.builder()
                                .reasonCode((String) c.get("reasonCode"))
                                .plainLanguage((String) c.get("plainLanguage"))
                                .weight(((Number) c.get("weight")).doubleValue())
                                .build()).toList() : List.of();

                return ModelOutput.builder()
                        .score(((Number) body.get("score")).doubleValue())
                        .confidence(((Number) body.get("confidence")).doubleValue())
                        .fallback((Boolean) body.get("fallback"))
                        .modelVersion((String) body.get("modelVersion"))
                        .thresholdPolicyVersion((String) body.get("thresholdPolicyVersion"))
                        .topContributions(contribList)
                        .build();
            }

            log.warn("Model service returned non-200: {}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("Model service call failed: {}", e.getMessage());
            return null;
        }
    }
}
