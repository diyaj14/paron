package org.paron.fraudservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.feature.RiskFeatureBuilder;
import org.paron.fraudservice.feature.RiskFeatureVector;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.policy.DecisionPolicy;
import org.paron.fraudservice.policy.DecisionResult;
import org.paron.fraudservice.policy.DecisionType;
import org.paron.fraudservice.policy.ModelClient;
import org.paron.fraudservice.policy.ModelOutput;
import org.paron.fraudservice.rules.FraudRule;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FraudScoringServiceTest {

    private FraudScoringService scoringService;
    private DecisionPolicy decisionPolicy;
    private ModelClient modelClient;
    private RiskFeatureBuilder featureBuilder;

    private static final RiskFeatureVector EMPTY_VECTOR = new RiskFeatureVector(
            "v1", 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0, 0, 1, 1, 1
    );

    @BeforeEach
    void setUp() {
        decisionPolicy = mock(DecisionPolicy.class);
        modelClient = mock(ModelClient.class);
        featureBuilder = mock(RiskFeatureBuilder.class);
        when(featureBuilder.build(any())).thenReturn(EMPTY_VECTOR);
    }

    private FraudScoringService buildService(List<FraudRule> rules) {
        return new FraudScoringService(rules, decisionPolicy, modelClient, featureBuilder);
    }

    private TransactionEvent buildEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setUserId("user-1");
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    @Test
    void noRules_modelApproves() {
        when(decisionPolicy.decide(eq(List.of()), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.APPROVE).reason("score_below_threshold").build());

        FraudScoringService service = buildService(Collections.emptyList());
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertTrue(result.isApproved());
        assertEquals(DecisionType.APPROVE.name(), result.getDecision());
    }

    @Test
    void hardRuleHit_alwaysRejects() {
        FraudRule highRule = mock(FraudRule.class);
        when(highRule.evaluate(any())).thenReturn(0.8);
        when(highRule.name()).thenReturn("HIGH_RULE");

        when(decisionPolicy.decide(eq(List.of("HIGH_RULE")), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.REJECT).reason("hard_rule_hit").build());

        FraudScoringService service = buildService(List.of(highRule));
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertFalse(result.isApproved());
        assertEquals(DecisionType.REJECT.name(), result.getDecision());
        assertEquals(List.of("HIGH_RULE"), result.getTriggeredRules());
    }

    @Test
    void modelUnavailable_holdsForReview() {
        when(modelClient.score(any(), any())).thenReturn(null);
        when(decisionPolicy.decide(eq(List.of()), isNull()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.HOLD_FOR_REVIEW).reason("model_unavailable").build());

        FraudScoringService service = buildService(Collections.emptyList());
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertFalse(result.isApproved());
        assertEquals(DecisionType.HOLD_FOR_REVIEW.name(), result.getDecision());
    }

    @Test
    void scoreClampedAtOne() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.evaluate(any())).thenReturn(0.8);
        when(rule1.name()).thenReturn("RULE_A");

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.evaluate(any())).thenReturn(0.8);
        when(rule2.name()).thenReturn("RULE_B");

        when(decisionPolicy.decide(eq(List.of("RULE_A", "RULE_B")), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.REJECT).reason("hard_rule_hit").build());

        FraudScoringService service = buildService(List.of(rule1, rule2));
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertEquals(1.0, result.getScore());
    }

    @Test
    void riskLevelMappedCorrectly() {
        FraudRule rule = mock(FraudRule.class);
        when(rule.evaluate(any())).thenReturn(0.5);
        when(rule.name()).thenReturn("RULE");

        when(decisionPolicy.decide(eq(List.of("RULE")), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.HOLD_FOR_REVIEW).reason("score_in_review_band").build());

        FraudScoringService service = buildService(List.of(rule));
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    void transactionIdPassedThrough() {
        when(decisionPolicy.decide(eq(List.of()), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.APPROVE).reason("score_below_threshold").build());

        FraudScoringService service = buildService(Collections.emptyList());
        TransactionEvent event = buildEvent();
        event.setDeviceTransactionId("txn-999");

        FraudAlertResponse result = service.evaluate(event);
        assertEquals("txn-999", result.getTransactionId());
    }

    @Test
    void lowSignalNewDevice_downgradesApproveToHold() {
        // historyAvailable == 0 -> a brand-new user/device must never
        // auto-approve, even though the policy would otherwise APPROVE.
        RiskFeatureVector noHistory = new RiskFeatureVector(
                "v1", 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0
        );
        when(featureBuilder.build(any())).thenReturn(noHistory);
        when(decisionPolicy.decide(eq(List.of()), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.APPROVE).reason("score_below_threshold").build());

        FraudScoringService service = buildService(Collections.emptyList());
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertFalse(result.isApproved());
        assertEquals(DecisionType.HOLD_FOR_REVIEW.name(), result.getDecision());
    }

    @Test
    void lowSignalNewDevice_stillRejectsOnHardRule() {
        // Low signal must never downgrade a genuine hard-rule rejection —
        // a replayed token stays a rejection regardless of history.
        RiskFeatureVector noHistory = new RiskFeatureVector(
                "v1", 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0
        );
        when(featureBuilder.build(any())).thenReturn(noHistory);
        when(decisionPolicy.decide(eq(List.of("HIGH_RULE")), any()))
                .thenReturn(DecisionResult.builder().decision(DecisionType.REJECT).reason("hard_rule_hit").build());

        FraudRule highRule = mock(FraudRule.class);
        when(highRule.evaluate(any())).thenReturn(0.8);
        when(highRule.name()).thenReturn("HIGH_RULE");

        FraudScoringService service = buildService(List.of(highRule));
        FraudAlertResponse result = service.evaluate(buildEvent());

        assertEquals(DecisionType.REJECT.name(), result.getDecision());
        assertFalse(result.isApproved());
    }
}
