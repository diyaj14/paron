package org.paron.fraudservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.rules.FraudRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudScoringService {

    private final List<FraudRule> rules;

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

        boolean approved = totalScore < scoreThreshold;
        double clampedScore = Math.min(totalScore, 1.0);
        RiskLevel riskLevel = mapRiskLevel(clampedScore);

        log.info("fraud check userId={},score={},approved={},rules={}",
                event.getUserId(), clampedScore, approved, triggeredRules);

        return FraudAlertResponse.builder()
                .transactionId(event.getDeviceTransactionId())
                .score(clampedScore)
                .approved(approved)
                .riskLevel(riskLevel)
                .triggeredRules(triggeredRules)
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
