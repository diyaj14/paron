package org.paron.fraudservice.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionPolicyTest {

    private DecisionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DecisionPolicy();
        ReflectionTestUtils.setField(policy, "approveBelow", 0.35);
        ReflectionTestUtils.setField(policy, "rejectAbove", 0.75);
        ReflectionTestUtils.setField(policy, "minConfidence", 0.6);
    }

    @Test
    void hardRuleHit_alwaysRejects() {
        DecisionResult result = policy.decide(
                List.of("VELOCITY_RULE"),
                ModelOutput.builder().score(0.1).confidence(0.9).fallback(false).build()
        );
        assertEquals(DecisionType.REJECT, result.getDecision());
        assertEquals("hard_rule_hit", result.getReason());
    }

    @Test
    void modelUnavailable_holdsForReview() {
        DecisionResult result = policy.decide(List.of(), null);
        assertEquals(DecisionType.HOLD_FOR_REVIEW, result.getDecision());
        assertEquals("model_unavailable", result.getReason());
    }

    @Test
    void modelFallback_holdsForReview() {
        DecisionResult result = policy.decide(List.of(),
                ModelOutput.builder().score(0.2).confidence(0.8).fallback(true).build());
        assertEquals(DecisionType.HOLD_FOR_REVIEW, result.getDecision());
        assertEquals("model_unavailable", result.getReason());
    }

    @Test
    void lowConfidence_holdsForReview() {
        DecisionResult result = policy.decide(List.of(),
                ModelOutput.builder().score(0.2).confidence(0.4).fallback(false).build());
        assertEquals(DecisionType.HOLD_FOR_REVIEW, result.getDecision());
        assertEquals("low_confidence", result.getReason());
    }

    @Test
    void scoreBelowThreshold_approves() {
        DecisionResult result = policy.decide(List.of(),
                ModelOutput.builder().score(0.2).confidence(0.9).fallback(false).build());
        assertEquals(DecisionType.APPROVE, result.getDecision());
        assertEquals("score_below_threshold", result.getReason());
    }

    @Test
    void scoreAboveThreshold_rejects() {
        DecisionResult result = policy.decide(List.of(),
                ModelOutput.builder().score(0.85).confidence(0.9).fallback(false).build());
        assertEquals(DecisionType.REJECT, result.getDecision());
        assertEquals("score_above_threshold", result.getReason());
    }

    @Test
    void scoreInReviewBand_holdsForReview() {
        DecisionResult result = policy.decide(List.of(),
                ModelOutput.builder().score(0.5).confidence(0.9).fallback(false).build());
        assertEquals(DecisionType.HOLD_FOR_REVIEW, result.getDecision());
        assertEquals("score_in_review_band", result.getReason());
    }

    @Test
    void noRulesNoModel_holdsForReview() {
        DecisionResult result = policy.decide(List.of(), null);
        assertEquals(DecisionType.HOLD_FOR_REVIEW, result.getDecision());
    }
}
