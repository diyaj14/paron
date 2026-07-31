package org.paron.fraudservice.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmountAnomalyRuleTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private AmountAnomalyRule rule;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // multiplier=3.0, minTransactions=5
        rule = new AmountAnomalyRule(redisTemplate, 3.0, 5);
    }

    private TransactionEvent buildEvent(String userId, double amount) {
        TransactionEvent event = new TransactionEvent();
        event.setUserId(userId);
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal(String.valueOf(amount)));
        return event;
    }

    @Test
    void belowMinTransactionsReturnsZero() {
        // count=3, total=300 → average=100, minTransactions=5 → return 0.0
        when(valueOps.increment("amt:total:user-1", 100.0)).thenReturn(300.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(3L);

        double score = rule.evaluate(buildEvent("user-1", 100.0));

        assertEquals(0.0, score);
    }

    @Test
    void atExactMinTransactionsAndBelowThreshold() {
        // count=5, total=500, currentAmount=100 → average=100, 100 > 100*3.0? No
        when(valueOps.increment("amt:total:user-1", 100.0)).thenReturn(500.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(5L);

        double score = rule.evaluate(buildEvent("user-1", 100.0));

        assertEquals(0.0, score);
    }

    @Test
    void aboveMultiplierReturnsOne() {
        // count=10, total=1000, currentAmount=500 → average=100, 500 > 300? Yes
        when(valueOps.increment("amt:total:user-1", 500.0)).thenReturn(1500.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(10L);

        double score = rule.evaluate(buildEvent("user-1", 500.0));

        assertEquals(1.0, score);
    }

    @Test
    void atExactMultiplierReturnsZero() {
        // count=10, total=1000, currentAmount=300 → average=100, 300 > 300? No (equal, not greater)
        when(valueOps.increment("amt:total:user-1", 300.0)).thenReturn(1300.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(10L);

        double score = rule.evaluate(buildEvent("user-1", 300.0));

        assertEquals(0.0, score);
    }

    @Test
    void justBelowMinTransactionsReturnsZero() {
        when(valueOps.increment("amt:total:user-1", 100.0)).thenReturn(400.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(4L);

        double score = rule.evaluate(buildEvent("user-1", 100.0));

        assertEquals(0.0, score);
    }

    @Test
    void keysExpireWith30DayTtl() {
        when(valueOps.increment("amt:total:user-1", 100.0)).thenReturn(100.0);
        when(valueOps.increment("amt:count:user-1")).thenReturn(1L);

        rule.evaluate(buildEvent("user-1", 100.0));

        verify(redisTemplate).expire("amt:total:user-1", Duration.ofDays(30));
        verify(redisTemplate).expire("amt:count:user-1", Duration.ofDays(30));
    }

    @Test
    void ruleNameIsAmountAnomaly() {
        assertEquals("AMOUNT_ANOMALY", rule.name());
    }
}
