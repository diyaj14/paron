package org.paron.syncservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.adjudicate.EvidenceCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/*
 * Turns the judge's deterministic ruling into a human-readable judgement
 * summary. If an LLM endpoint is configured it asks the model to write the
 * summary as an adversarial reviewer — but the summary is STRICTLY advisory:
 * the binding ruling always comes from the deterministic evidence gates in
 * DisputeAdjudicator. No LLM key -> a clear templated summary is used, so the
 * demo never depends on an external service being reachable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeReasoner {

    private final RestTemplate restTemplate;

    @Value("${adjudicator.llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${adjudicator.llm.url:}")
    private String llmUrl;

    @Value("${adjudicator.llm.api-key:}")
    private String llmApiKey;

    @Value("${adjudicator.llm.model:gpt-4o-mini}")
    private String llmModel;

    public String summarize(String ruling, String winnerId, List<EvidenceCheck> evidence) {
        if (llmEnabled && !llmUrl.isBlank()) {
            try {
                return callLlm(ruling, winnerId, evidence);
            } catch (Exception e) {
                log.warn("LLM judge unavailable, falling back to template summary. error={}", e.getMessage());
            }
        }
        return template(ruling, winnerId, evidence);
    }

    private String template(String ruling, String winnerId, List<EvidenceCheck> evidence) {
        long passed = evidence.stream().filter(EvidenceCheck::isPassed).count();
        StringBuilder sb = new StringBuilder();
        sb.append("Ruling: ").append(ruling);
        if (winnerId != null && !winnerId.isBlank()) {
            sb.append(". The valid claim belongs to receipt ").append(winnerId).append(".");
        }
        sb.append(" ").append(passed).append("/").append(evidence.size())
          .append(" evidence checks passed.");
        for (EvidenceCheck check : evidence) {
            sb.append(" [").append(check.getCheck()).append(": ").append(check.isPassed() ? "OK" : "FAIL").append("]");
        }
        return sb.toString();
    }

    private String callLlm(String ruling, String winnerId, List<EvidenceCheck> evidence) {
        StringBuilder evidenceBlock = new StringBuilder();
        for (EvidenceCheck check : evidence) {
            evidenceBlock.append("- ").append(check.getCheck()).append(": ")
                    .append(check.isPassed() ? "PASSED" : "FAILED")
                    .append(" (").append(check.getDetail()).append(")\n");
        }
        String user = "Ruling already determined deterministically: " + ruling
                + ". Winner receipt: " + (winnerId == null ? "none" : winnerId) + ".\n\n"
                + "Evidence the judge gathered:\n" + evidenceBlock
                + "\nWrite a short (3-4 sentences) human-readable verdict explaining the decision to a merchant and customer. Do not change the ruling.";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!llmApiKey.isBlank()) {
            headers.setBearerAuth(llmApiKey);
        }
        Map<String, Object> body = Map.of(
                "model", llmModel,
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are a rigorous, neutral payments dispute adjudicator. Base every statement strictly on the supplied evidence."),
                        Map.of("role", "user", "content", user)));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(llmUrl, request, Map.class);
        Object content = extractContent(response.getBody());
        return content != null ? content.toString().trim() : template(ruling, winnerId, evidence);
    }

    /* OpenAI-compatible chat completions: choices[0].message.content */
    private Object extractContent(Map<?, ?> body) {
        if (body == null) return null;
        Object choices = body.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
            return message.get("content");
        }
        return null;
    }
}