package org.paron.fraudservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.rules.FraudRule;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FraudScoringServiceTest {

    private FraudScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new FraudScoringService(List.of());
        ReflectionTestUtils.setField(scoringService, "scoreThreshold", 0.7);
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
    void noRulesApproved() {
        FraudScoringService service = new FraudScoringService(Collections.emptyList());
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertTrue(result.isApproved());
        assertEquals(0.0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getTriggeredRules().isEmpty());
    }

    @Test
    void singleLowScoreApproved() {
        FraudRule lowRule = mock(FraudRule.class);
        when(lowRule.evaluate(any())).thenReturn(0.2);
        when(lowRule.name()).thenReturn("LOW_RULE");

        FraudScoringService service = new FraudScoringService(List.of(lowRule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertTrue(result.isApproved());
        assertEquals(0.2, result.getScore(), 0.001);
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertEquals(List.of("LOW_RULE"), result.getTriggeredRules());
    }

    @Test
    void singleHighScoreRejected() {
        FraudRule highRule = mock(FraudRule.class);
        when(highRule.evaluate(any())).thenReturn(0.8);
        when(highRule.name()).thenReturn("HIGH_RULE");

        FraudScoringService service = new FraudScoringService(List.of(highRule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertFalse(result.isApproved());
        assertEquals(0.8, result.getScore(), 0.001);
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertEquals(List.of("HIGH_RULE"), result.getTriggeredRules());
    }

    @Test
    void multipleRulesScoresAggregated() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.evaluate(any())).thenReturn(0.3);
        when(rule1.name()).thenReturn("RULE_A");

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.evaluate(any())).thenReturn(0.3);
        when(rule2.name()).thenReturn("RULE_B");

        FraudScoringService service = new FraudScoringService(List.of(rule1, rule2));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertTrue(result.isApproved());
        assertEquals(0.6, result.getScore(), 0.001);
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        assertEquals(List.of("RULE_A", "RULE_B"), result.getTriggeredRules());
    }

    @Test
    void aggregatedScoreExceedsThresholdRejected() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.evaluate(any())).thenReturn(0.5);
        when(rule1.name()).thenReturn("RULE_A");

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.evaluate(any())).thenReturn(0.5);
        when(rule2.name()).thenReturn("RULE_B");

        FraudScoringService service = new FraudScoringService(List.of(rule1, rule2));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertFalse(result.isApproved());
        assertEquals(1.0, result.getScore(), 0.001);
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    void scoreClampedAtOne() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.evaluate(any())).thenReturn(0.8);
        when(rule1.name()).thenReturn("RULE_A");

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.evaluate(any())).thenReturn(0.8);
        when(rule2.name()).thenReturn("RULE_B");

        FraudRule rule3 = mock(FraudRule.class);
        when(rule3.evaluate(any())).thenReturn(0.8);
        when(rule3.name()).thenReturn("RULE_C");

        FraudScoringService service = new FraudScoringService(List.of(rule1, rule2, rule3));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertEquals(1.0, result.getScore());
    }

    @Test
    void zeroScoreRulesSkipped() {
        FraudRule zeroRule = mock(FraudRule.class);
        when(zeroRule.evaluate(any())).thenReturn(0.0);
        when(zeroRule.name()).thenReturn("ZERO_RULE");

        FraudRule highRule = mock(FraudRule.class);
        when(highRule.evaluate(any())).thenReturn(0.5);
        when(highRule.name()).thenReturn("HIGH_RULE");

        FraudScoringService service = new FraudScoringService(List.of(zeroRule, highRule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());

        assertEquals(0.5, result.getScore(), 0.001);
        assertEquals(List.of("HIGH_RULE"), result.getTriggeredRules());
    }

    @Test
    void riskLevelLowBelowPoint3() {
        FraudRule rule = mock(FraudRule.class);
        when(rule.evaluate(any())).thenReturn(0.1);
        when(rule.name()).thenReturn("RULE");

        FraudScoringService service = new FraudScoringService(List.of(rule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void riskLevelMediumBetweenPoint3AndPoint6() {
        FraudRule rule = mock(FraudRule.class);
        when(rule.evaluate(any())).thenReturn(0.5);
        when(rule.name()).thenReturn("RULE");

        FraudScoringService service = new FraudScoringService(List.of(rule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    @Test
    void riskLevelHighBetweenPoint6AndPoint9() {
        FraudRule rule = mock(FraudRule.class);
        when(rule.evaluate(any())).thenReturn(0.8);
        when(rule.name()).thenReturn("RULE");

        FraudScoringService service = new FraudScoringService(List.of(rule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void riskLevelCriticalAbovePoint9() {
        FraudRule rule = mock(FraudRule.class);
        when(rule.evaluate(any())).thenReturn(1.0);
        when(rule.name()).thenReturn("RULE");

        FraudScoringService service = new FraudScoringService(List.of(rule));
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        FraudAlertResponse result = service.evaluate(buildEvent());
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
    }

    @Test
    void transactionIdPassedThrough() {
        FraudScoringService service = new FraudScoringService(Collections.emptyList());
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.7);

        TransactionEvent event = buildEvent();
        event.setDeviceTransactionId("txn-999");

        FraudAlertResponse result = service.evaluate(event);
        assertEquals("txn-999", result.getTransactionId());
    }
}
