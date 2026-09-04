package org.paron.fraudservice.policy;

import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionPolicy {

    @Value("${policy.approve-below:0.15}")
    private double approveBelow;

    @Value("${policy.reject-above:0.75}")
    private double rejectAbove;

    @Value("${policy.min-confidence:0.6}")
    private double minConfidence;

    public DecisionResult decide(List<String> ruleHits, ModelOutput modelOutput) {
        if (!ruleHits.isEmpty()) {
            return DecisionResult.builder()
                    .decision(DecisionType.REJECT)
                    .reason("hard_rule_hit")
                    .triggeredRules(ruleHits)
                    .score(modelOutput != null ? modelOutput.getScore() : null)
                    .confidence(modelOutput != null ? modelOutput.getConfidence() : null)
                    .build();
        }

        if (modelOutput == null || modelOutput.isFallback()) {
            return DecisionResult.builder()
                    .decision(DecisionType.HOLD_FOR_REVIEW)
                    .reason("model_unavailable")
                    .triggeredRules(List.of())
                    .score(null)
                    .confidence(null)
                    .build();
        }

        if (modelOutput.getConfidence() < minConfidence) {
            return DecisionResult.builder()
                    .decision(DecisionType.HOLD_FOR_REVIEW)
                    .reason("low_confidence")
                    .triggeredRules(List.of())
                    .score(modelOutput.getScore())
                    .confidence(modelOutput.getConfidence())
                    .build();
        }

        double score = modelOutput.getScore();
        if (score < approveBelow) {
            return DecisionResult.builder()
                    .decision(DecisionType.APPROVE)
                    .reason("score_below_threshold")
                    .triggeredRules(List.of())
                    .score(score)
                    .confidence(modelOutput.getConfidence())
                    .build();
        }

        if (score >= rejectAbove) {
            return DecisionResult.builder()
                    .decision(DecisionType.REJECT)
                    .reason("score_above_threshold")
                    .triggeredRules(List.of())
                    .score(score)
                    .confidence(modelOutput.getConfidence())
                    .build();
        }

        return DecisionResult.builder()
                .decision(DecisionType.HOLD_FOR_REVIEW)
                .reason("score_in_review_band")
                .triggeredRules(List.of())
                .score(score)
                .confidence(modelOutput.getConfidence())
                .build();
    }
}
