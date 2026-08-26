package org.paron.fraudservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.feature.RiskFeatureBuilder;
import org.paron.fraudservice.feature.RiskFeatureVector;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.policy.DecisionPolicy;
import org.paron.fraudservice.policy.DecisionResult;
import org.paron.fraudservice.policy.DecisionType;
import org.paron.fraudservice.policy.ModelClient;
import org.paron.fraudservice.policy.ModelOutput;
import org.paron.fraudservice.rules.FraudRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudScoringService {

    private final List<FraudRule> rules;
    private final DecisionPolicy decisionPolicy;
    private final ModelClient modelClient;
    private final RiskFeatureBuilder featureBuilder;

    @Value("${fraud.score-threshold:0.7}")
    private double scoreThreshold;

    public FraudAlertResponse evaluate(TransactionEvent event) {
        List<String> triggeredRules = new ArrayList<>();
        double totalScore = 0.0;

        for (FraudRule rule : rules) {
            double score = rule.evaluate(event);
            if (score > 0.0) {
                totalScore += score;
                triggeredRules.add(rule.name());
            }
        }

        RiskFeatureVector featureVector = featureBuilder.build(event);
        Map<String, Double> features = featureVector.toMap();
        String correlationId = UUID.randomUUID().toString();
        ModelOutput modelOutput = modelClient.score(features, correlationId);

        DecisionResult decisionResult = decisionPolicy.decide(triggeredRules, modelOutput);

        boolean approved = decisionResult.getDecision() == DecisionType.APPROVE;
        double clampedScore = Math.min(totalScore, 1.0);
        RiskLevel riskLevel = mapRiskLevel(clampedScore);

        List<String> reasonCodes = modelOutput != null && modelOutput.getTopContributions() != null ?
                modelOutput.getTopContributions().stream()
                        .map(ModelOutput.Contribution::getReasonCode)
                        .toList() : List.of();

        log.info("fraud check userId={},decision={},score={},rules={},modelScore={}",
                event.getUserId(), decisionResult.getDecision(), clampedScore,
                triggeredRules, modelOutput != null ? modelOutput.getScore() : null);

        return FraudAlertResponse.builder()
                .transactionId(event.getDeviceTransactionId())
                .score(clampedScore)
                .approved(approved)
                .riskLevel(riskLevel)
                .triggeredRules(triggeredRules)
                .decision(decisionResult.getDecision().name())
                .confidence(modelOutput != null ? modelOutput.getConfidence() : null)
                .modelVersion(modelOutput != null ? modelOutput.getModelVersion() : null)
                .policyVersion("thresholds-v1")
                .reasonCodes(reasonCodes)
                .build();
    }

    private RiskLevel mapRiskLevel(double score) {
        if (score == 0.0) return RiskLevel.LOW;
        if (score <= 0.3) return RiskLevel.LOW;
        if (score <= 0.6) return RiskLevel.MEDIUM;
        if (score <= 0.9) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }
}
