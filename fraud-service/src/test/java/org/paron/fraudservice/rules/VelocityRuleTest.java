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

class VelocityRuleTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new VelocityRule(redisTemplate, 60, 5);
    }

    private TransactionEvent buildEvent(String userId) {
        TransactionEvent event = new TransactionEvent();
        event.setUserId(userId);
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    @Test
    void firstTransactionReturnsZero() {
        when(valueOps.increment("velocity:user-1")).thenReturn(1L);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(0.0, score);
        verify(redisTemplate).expire("velocity:user-1", Duration.ofSeconds(60));
    }

    @Test
    void withinLimitReturnsZero() {
        when(valueOps.increment("velocity:user-1")).thenReturn(3L);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(0.0, score);
        verify(redisTemplate, never()).expire(eq("velocity:user-1"), any(Duration.class));
    }

    @Test
    void atExactLimitReturnsZero() {
        when(valueOps.increment("velocity:user-1")).thenReturn(5L);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(0.0, score);
    }

    @Test
    void exceedsLimitReturnsOne() {
        when(valueOps.increment("velocity:user-1")).thenReturn(6L);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(1.0, score);
    }

    @Test
    void farExceedsLimitReturnsOne() {
        when(valueOps.increment("velocity:user-1")).thenReturn(20L);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(1.0, score);
    }

    @Test
    void nullCountReturnsZero() {
        when(valueOps.increment("velocity:user-1")).thenReturn(null);

        double score = rule.evaluate(buildEvent("user-1"));

        assertEquals(0.0, score);
    }

    @Test
    void ttlOnlySetOnFirstTransaction() {
        when(valueOps.increment("velocity:user-1")).thenReturn(1L);
        rule.evaluate(buildEvent("user-1"));

        when(valueOps.increment("velocity:user-1")).thenReturn(2L);
        rule.evaluate(buildEvent("user-1"));

        verify(redisTemplate, times(1)).expire("velocity:user-1", Duration.ofSeconds(60));
    }

    @Test
    void ruleNameIsVelocityBreach() {
        assertEquals("VELOCITY_BREACH", rule.name());
    }
}
