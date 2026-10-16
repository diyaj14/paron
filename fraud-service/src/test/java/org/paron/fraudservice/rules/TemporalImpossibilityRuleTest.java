package org.paron.fraudservice.rules;

import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.TransactionEvent;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TemporalImpossibilityRuleTest {

    private final TemporalImpossibilityRule rule = new TemporalImpossibilityRule();

    private TransactionEvent buildEvent(String transactedAt, String tokenExpiryTime) {
        TransactionEvent event = new TransactionEvent();
        event.setUserId("user-1");
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        event.setTransactedAt(transactedAt);
        event.setTokenExpiryTime(tokenExpiryTime);
        return event;
    }

    @Test
    void nullTransactedAtReturnsZero() {
        double score = rule.evaluate(buildEvent(null, "2026-07-28T15:00:00"));
        assertEquals(0.0, score);
    }

    @Test
    void blankTransactedAtReturnsZero() {
        double score = rule.evaluate(buildEvent("  ", "2026-07-28T15:00:00"));
        assertEquals(0.0, score);
    }

    @Test
    void nullTokenExpiryReturnsZero() {
        double score = rule.evaluate(buildEvent("2026-07-28T14:00:00", null));
        assertEquals(0.0, score);
    }

    @Test
    void blankTokenExpiryReturnsZero() {
        double score = rule.evaluate(buildEvent("2026-07-28T14:00:00", "  "));
        assertEquals(0.0, score);
    }

    @Test
    void transactedAfterExpiryReturnsOne() {
        double score = rule.evaluate(buildEvent("2026-07-28T18:00:00", "2026-07-28T15:00:00"));
        assertEquals(1.0, score);
    }

    @Test
    void transactedBeforeExpiryReturnsZero() {
        double score = rule.evaluate(buildEvent("2026-07-28T14:00:00", "2026-07-28T15:00:00"));
        assertEquals(0.0, score);
    }

    @Test
    void transactedAtExactExpiryReturnsZero() {
        double score = rule.evaluate(buildEvent("2026-07-28T15:00:00", "2026-07-28T15:00:00"));
        assertEquals(0.0, score);
    }

    @Test
    void invalidDateFormatReturnsZero() {
        double score = rule.evaluate(buildEvent("not-a-date", "2026-07-28T15:00:00"));
        assertEquals(0.0, score);
    }

    @Test
    void ruleNameIsTemporalImpossibility() {
        assertEquals("TEMPORAL_IMPOSSIBILITY", rule.name());
    }
}
