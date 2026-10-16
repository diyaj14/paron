package org.paron.fraudservice.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimePatternRuleTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ZSetOperations<String, String> zSetOps;
    private TimePatternRule rule;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        rule = new TimePatternRule(redisTemplate, 10);
    }

    private TransactionEvent buildEvent(String userId, String transactedAt) {
        TransactionEvent event = new TransactionEvent();
        event.setUserId(userId);
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        event.setTransactedAt(transactedAt);
        return event;
    }

    @Test
    void belowMinTransactionsReturnsZero() {
        when(valueOps.increment("time:count:user-1")).thenReturn(5L);

        double score = rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        assertEquals(0.0, score);
    }

    @Test
    void hourNeverSeenReturnsPointSeven() {
        when(valueOps.increment("time:count:user-1")).thenReturn(10L);
        when(zSetOps.score("time:hours:user-1", "14")).thenReturn(null);

        double score = rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        assertEquals(0.7, score);
    }

    @Test
    void hourScoreZeroReturnsPointSeven() {
        when(valueOps.increment("time:count:user-1")).thenReturn(10L);
        when(zSetOps.score("time:hours:user-1", "14")).thenReturn(0.0);

        double score = rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        assertEquals(0.7, score);
    }

    @Test
    void hourPreviouslySeenReturnsZero() {
        when(valueOps.increment("time:count:user-1")).thenReturn(10L);
        when(zSetOps.score("time:hours:user-1", "14")).thenReturn(3.0);

        double score = rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        assertEquals(0.0, score);
    }

    @Test
    void keysExpireWith30DayTtl() {
        when(valueOps.increment("time:count:user-1")).thenReturn(1L);

        rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        verify(redisTemplate).expire("time:hours:user-1", Duration.ofDays(30));
        verify(redisTemplate).expire("time:count:user-1", Duration.ofDays(30));
    }

    @Test
    void hourIsIncrementedInSortedSet() {
        when(valueOps.increment("time:count:user-1")).thenReturn(1L);

        rule.evaluate(buildEvent("user-1", "2026-07-28T14:30:00"));

        verify(zSetOps).incrementScore("time:hours:user-1", "14", 1);
    }

    @Test
    void nullTransactedAtUsesCurrentHour() {
        when(valueOps.increment("time:count:user-1")).thenReturn(10L);
        when(zSetOps.score(anyString(), anyString())).thenReturn(3.0);

        double score = rule.evaluate(buildEvent("user-1", null));

        assertEquals(0.0, score);
        verify(zSetOps).incrementScore(eq("time:hours:user-1"), anyString(), eq(1.0));
    }

    @Test
    void ruleNameIsTimePattern() {
        assertEquals("TIME_PATTERN", rule.name());
    }
}
