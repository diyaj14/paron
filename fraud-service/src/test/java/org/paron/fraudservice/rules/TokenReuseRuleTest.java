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

class TokenReuseRuleTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private TokenReuseRule rule;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new TokenReuseRule(redisTemplate, 24);
    }

    private TransactionEvent buildEvent(String userId, String token, String deviceId) {
        TransactionEvent event = new TransactionEvent();
        event.setUserId(userId);
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken(token);
        event.setDeviceId(deviceId);
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    @Test
    void nullTokenReturnsZero() {
        double score = rule.evaluate(buildEvent("user-1", null, "device-1"));
        assertEquals(0.0, score);
    }

    @Test
    void blankTokenReturnsZero() {
        double score = rule.evaluate(buildEvent("user-1", "  ", "device-1"));
        assertEquals(0.0, score);
    }

    @Test
    void nullDeviceIdReturnsZero() {
        double score = rule.evaluate(buildEvent("user-1", "token-1", null));
        assertEquals(0.0, score);
    }

    @Test
    void blankDeviceIdReturnsZero() {
        double score = rule.evaluate(buildEvent("user-1", "token-1", "  "));
        assertEquals(0.0, score);
    }

    @Test
    void firstTimeTokenStoredReturnsZero() {
        when(valueOps.get("token:tok-abc")).thenReturn(null);

        double score = rule.evaluate(buildEvent("user-1", "tok-abc", "device-1"));

        assertEquals(0.0, score);
        verify(valueOps).set("token:tok-abc", "device-1", Duration.ofHours(24));
    }

    @Test
    void sameDeviceReturnsZero() {
        when(valueOps.get("token:tok-abc")).thenReturn("device-1");

        double score = rule.evaluate(buildEvent("user-1", "tok-abc", "device-1"));

        assertEquals(0.0, score);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void differentDeviceReturnsOne() {
        when(valueOps.get("token:tok-abc")).thenReturn("device-1");

        double score = rule.evaluate(buildEvent("user-1", "tok-abc", "device-2"));

        assertEquals(1.0, score);
    }

    @Test
    void ruleNameIsTokenReuse() {
        assertEquals("TOKEN_REUSE", rule.name());
    }
}
