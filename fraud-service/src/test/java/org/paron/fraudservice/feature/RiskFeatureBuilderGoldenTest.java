package org.paron.fraudservice.feature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskFeatureBuilderGoldenTest {

    private static final Instant NOW = Instant.parse("2026-08-24T10:15:30Z");

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zsetOps;
    private ValueOperations<String, String> valueOps;
    private RiskFeatureBuilder builder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zsetOps = mock(ZSetOperations.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        builder = new RiskFeatureBuilder(redis, fixed, 5000.0, 21600L, 0.12);
    }

    private TransactionEvent fixtureEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setUserId("user-1");
        event.setDeviceTransactionId("txn-abc");
        event.setOfflineToken("tok-secret");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("1250.00"));
        event.setMerchantId("merch-1");
        event.setTransactedAt("2026-08-24T10:00:30Z");
        event.setTokenExpiryTime("2026-08-24T15:15:30Z");
        return event;
    }

    @Test
    void goldenVectorMatchesFrozenContract() {
        RiskFeatureVector v = builder.build(fixtureEvent());

        assertEquals(RiskFeatureVector.SCHEMA_VERSION, v.featureSchemaVersion());
        assertEquals(1250.0, v.amount());
        assertEquals(0.25, v.amountToTokenLimitRatio());
        assertEquals(0.0, v.merchantAmountDeviation());
        assertEquals(0, v.userTxCount5m());
        assertEquals(0, v.userTxCount1h());
        assertEquals(0, v.userTxCount24h());
        assertEquals(0, v.deviceTxCount5m());
        assertEquals(0, v.deviceTxCount1h());
        assertEquals(0, v.deviceTxCount24h());
        assertEquals(1, v.tokenTxCount24h());
        assertEquals(0.0, v.userTxValue1h());
        assertEquals(0.0, v.deviceTxValue24h());
        assertEquals(3600.0, v.tokenAgeSeconds());
        assertEquals(18000.0, v.timeToExpirySeconds());
        assertEquals(900.0, v.offlineDurationSeconds());
        assertEquals(0, v.tokenReuseCount());
        assertEquals(0, v.duplicatePayloadHashCount());
        assertEquals(0, v.previousSettlementFailed());
        assertEquals(0.12, v.merchantRiskAggregate());
        assertEquals(10, v.hourOfDay());
        assertEquals(0, v.dayOfWeek());
        assertEquals(0, v.historyAvailable());
        assertEquals(1, v.tokenAgeKnown());
        assertEquals(1, v.expiryKnown());
    }

    @Test
    void unparseableTimestampsYieldUnknownFlagsNotZeros() {
        TransactionEvent event = fixtureEvent();
        event.setTransactedAt("");
        event.setTokenExpiryTime("not-a-date");

        RiskFeatureVector vector = builder.build(event);

        assertNull(vector.tokenAgeSeconds());
        assertNull(vector.timeToExpirySeconds());
        assertEquals(0, vector.tokenAgeKnown());
        assertEquals(0, vector.expiryKnown());
        assertEquals(0.0, vector.offlineDurationSeconds());
        assertEquals(NOW.atZone(ZoneOffset.UTC).getHour(), vector.hourOfDay());
    }

    @Test
    void vectorNeverContainsRawIdentifiers() {
        RiskFeatureVector v = builder.build(fixtureEvent());

        String json = v.toString();
        assertFalse(json.contains("tok-secret"));
        assertFalse(json.contains("user-1"));
        assertFalse(json.contains("device-1"));
        assertFalse(json.contains("txn-abc"));
        assertFalse(json.contains("merch-1"));
    }

    @Test
    void merchantHistoryDrivesAmountDeviation() {
        TransactionEvent event = fixtureEvent();
        event.setAmount(new BigDecimal("2500.00"));

        when(valueOps.increment("feat:merchant:merch-1:cnt", 1.0)).thenReturn(9.0);
        when(valueOps.increment("feat:merchant:merch-1:sum", 2500.0)).thenReturn(12500.0);

        RiskFeatureVector vector = builder.build(event);

        assertEquals(1.0, vector.merchantAmountDeviation());
        verify(valueOps).increment("feat:merchant:merch-1:sum", 2500.0);
        verify(valueOps).increment("feat:merchant:merch-1:cnt", 1.0);
    }

    @Test
    void buildRecordsObservationIntoWindows() {
        builder.build(fixtureEvent());

        verify(zsetOps).add(
                eq("feat:user:user-1:events"),
                contains("txn-abc"),
                eq((double) NOW.toEpochMilli())
        );
        verify(zsetOps).add(
                eq("feat:device:device-1:events"),
                contains("txn-abc"),
                eq((double) NOW.toEpochMilli())
        );
        verify(redis).expire("feat:user:user-1:events", Duration.ofHours(25));
        verify(redis).expire("feat:device:device-1:events", Duration.ofHours(25));
    }

    @Test
    void priorTokenUseIsReportedThenIncremented() {
        when(valueOps.increment(anyString())).thenReturn(3L);

        RiskFeatureVector vector = builder.build(fixtureEvent());

        assertEquals(2, vector.tokenReuseCount());
        verify(valueOps).increment(startsWith("feat:tokuse:"));
    }

    @Test
    void nullRedisCountersDegradeGracefully() {
        when(zsetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(null);
        when(zsetOps.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(java.util.Set.of());
        when(valueOps.increment(anyString())).thenReturn(null);
        when(valueOps.increment(anyString(), anyDouble())).thenReturn(null);

        RiskFeatureVector vector = builder.build(fixtureEvent());

        assertEquals(0, vector.userTxCount24h());
        assertEquals(0.0, vector.userTxValue1h());
        assertEquals(0, vector.tokenReuseCount());
        assertEquals(0.0, vector.merchantAmountDeviation());
    }
}
