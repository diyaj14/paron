package org.paron.fraudservice.feature;

import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Set;

@Component
public class RiskFeatureBuilder {

    private static final String USER_EVENTS_KEY = "feat:user:%s:events";
    private static final String DEVICE_EVENTS_KEY = "feat:device:%s:events";
    private static final String TOKEN_USE_KEY = "feat:tokuse:%s";
    private static final String DUP_KEY = "feat:dup:%s";
    private static final String SETTLE_FAIL_KEY = "feat:settlefail:%s";
    private static final String MERCHANT_SUM_KEY = "feat:merchant:%s:sum";
    private static final String MERCHANT_CNT_KEY = "feat:merchant:%s:cnt";
    private static final String MERCHANT_RISK_KEY = "feat:merchant:%s:risk";

    private static final long WINDOW_5M = Duration.ofMinutes(5).toMillis();
    private static final long WINDOW_1H = Duration.ofHours(1).toMillis();
    private static final long WINDOW_24H = Duration.ofHours(24).toMillis();
    private static final Duration EVENT_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final double tokenLimit;
    private final long tokenValiditySeconds;
    private final double defaultMerchantRisk;

    @Autowired
    public RiskFeatureBuilder(
            StringRedisTemplate redis,
            @Value("${fraud.features.token-limit-rupees:5000}") double tokenLimit,
            @Value("${fraud.features.token-validity-seconds:21600}") long tokenValiditySeconds,
            @Value("${fraud.features.default-merchant-risk:0.12}") double defaultMerchantRisk
    ) {
        this(redis, Clock.systemUTC(), tokenLimit, tokenValiditySeconds, defaultMerchantRisk);
    }

    RiskFeatureBuilder(
            StringRedisTemplate redis,
            Clock clock,
            double tokenLimit,
            long tokenValiditySeconds,
            double defaultMerchantRisk
    ) {
        this.redis = redis;
        this.clock = clock;
        this.tokenLimit = tokenLimit;
        this.tokenValiditySeconds = tokenValiditySeconds;
        this.defaultMerchantRisk = defaultMerchantRisk;
    }

    public RiskFeatureVector build(TransactionEvent event) {
        Instant now = clock.instant();
        long nowMs = now.toEpochMilli();
        BigDecimal amount = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();

        Instant txTime = parseInstant(event.getTransactedAt());
        if (txTime == null) {
            txTime = now;
        }
        double offlineDuration = Math.max(0, (nowMs - txTime.toEpochMilli()) / 1000.0);

        Double timeToExpiry = null;
        Integer expiryKnown = 0;
        if (event.getTokenExpiryTime() != null && !event.getTokenExpiryTime().isBlank()) {
            Instant expiry = parseInstant(event.getTokenExpiryTime());
            if (expiry != null) {
                timeToExpiry = round4((expiry.toEpochMilli() - nowMs) / 1000.0);
                expiryKnown = 1;
            }
        }

        Double tokenAge = null;
        Integer tokenAgeKnown = 0;
        if (timeToExpiry != null) {
            tokenAge = round4(Math.max(0, tokenValiditySeconds) - timeToExpiry);
            tokenAgeKnown = 1;
        }

        Windows userWindows = readWindows(String.format(USER_EVENTS_KEY, event.getUserId()), nowMs);
        Windows deviceWindows = readWindows(String.format(DEVICE_EVENTS_KEY, event.getDeviceId()), nowMs);

        int tokenReuseCount = countPriorUse(String.format(TOKEN_USE_KEY, sha256Hex(event.getOfflineToken())));
        int duplicatePayloadCount = countPriorUse(String.format(DUP_KEY, payloadHash(event)));
        int previousSettlementFailed = "1".equals(valueOf(String.format(SETTLE_FAIL_KEY, event.getUserId()))) ? 1 : 0;

        double merchantDeviation = merchantAmountDeviation(event.getMerchantId(), amount);
        double merchantRisk = merchantRisk(event.getMerchantId());

        recordObservation(event, amount, nowMs);

        return new RiskFeatureVector(
                RiskFeatureVector.SCHEMA_VERSION,
                round4(amount.doubleValue()),
                round4(amount.doubleValue() / tokenLimit),
                round4(merchantDeviation),
                (int) userWindows.count5m(),
                (int) userWindows.count1h(),
                (int) userWindows.count24h(),
                (int) deviceWindows.count5m(),
                (int) deviceWindows.count1h(),
                (int) deviceWindows.count24h(),
                tokenReuseCount + 1,
                round4(userWindows.value1h()),
                round4(deviceWindows.value24h()),
                tokenAge,
                timeToExpiry,
                round4(offlineDuration),
                tokenReuseCount,
                duplicatePayloadCount,
                previousSettlementFailed,
                round4(merchantRisk),
                txTime.atZone(ZoneOffset.UTC).getHour(),
                (txTime.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1),
                (userWindows.count24h() > 0 || deviceWindows.count24h() > 0) ? 1 : 0,
                tokenAgeKnown,
                expiryKnown
        );
    }

    private Windows readWindows(String key, long nowMs) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.removeRangeByScore(key, 0.0, (double) (nowMs - WINDOW_24H));

        long c5 = longValue(zset.count(key, (double) (nowMs - WINDOW_5M), (double) nowMs));
        long c1 = longValue(zset.count(key, (double) (nowMs - WINDOW_1H), (double) nowMs));
        long c24 = longValue(zset.count(key, (double) (nowMs - WINDOW_24H), (double) nowMs));

        double v1 = sumValues(zset.rangeByScore(key, (double) (nowMs - WINDOW_1H), (double) nowMs));
        double v24 = sumValues(zset.rangeByScore(key, (double) (nowMs - WINDOW_24H), (double) nowMs));

        return new Windows(c5, c1, c24, v1, v24);
    }

    private void recordObservation(TransactionEvent event, BigDecimal amount, long nowMs) {
        String member = event.getDeviceTransactionId() + ":" + nowMs + "|" + amount.toPlainString();
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.add(String.format(USER_EVENTS_KEY, event.getUserId()), member, nowMs);
        if (event.getDeviceId() != null && !event.getDeviceId().isBlank()) {
            zset.add(String.format(DEVICE_EVENTS_KEY, event.getDeviceId()), member, nowMs);
        }
        redis.expire(String.format(USER_EVENTS_KEY, event.getUserId()), EVENT_TTL);
        if (event.getDeviceId() != null && !event.getDeviceId().isBlank()) {
            redis.expire(String.format(DEVICE_EVENTS_KEY, event.getDeviceId()), EVENT_TTL);
        }
    }

    private int countPriorUse(String key) {
        Long newValue = redis.opsForValue().increment(key);
        if (newValue == null) {
            return 0;
        }
        redis.expire(key, EVENT_TTL);
        return (int) Math.max(0, newValue - 1);
    }

    private double merchantAmountDeviation(String merchantId, BigDecimal amount) {
        if (merchantId == null || merchantId.isBlank()) {
            return 0.0;
        }
        Double cnt = redis.opsForValue().increment(String.format(MERCHANT_CNT_KEY, merchantId), 1.0);
        Double sum = redis.opsForValue().increment(String.format(MERCHANT_SUM_KEY, merchantId), amount.doubleValue());
        if (cnt == null || sum == null || cnt <= 1.0) {
            return 0.0;
        }
        double priorAvg = (sum - amount.doubleValue()) / (cnt - 1.0);
        if (priorAvg <= 0.0) {
            return 0.0;
        }
        return Math.abs(amount.doubleValue() - priorAvg) / priorAvg;
    }

    private double merchantRisk(String merchantId) {
        String stored = valueOf(String.format(MERCHANT_RISK_KEY, merchantId));
        if (stored == null || stored.isBlank()) {
            return defaultMerchantRisk;
        }
        try {
            double parsed = Double.parseDouble(stored);
            return Math.min(1.0, Math.max(0.0, parsed));
        } catch (NumberFormatException ex) {
            return defaultMerchantRisk;
        }
    }

    private String payloadHash(TransactionEvent event) {
        String canonical = String.join("|",
                safe(event.getUserId()),
                safe(event.getDeviceTransactionId()),
                event.getAmount() == null ? "" : event.getAmount().toPlainString(),
                safe(event.getTransactedAt()),
                sha256Hex(safe(event.getOfflineToken()))
        );
        return sha256Hex(canonical);
    }

    private String valueOf(String key) {
        return redis.opsForValue().get(key);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw.trim()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw.trim()).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static double sumValues(Set<String> members) {
        if (members == null || members.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (String member : members) {
            int idx = member.lastIndexOf('|');
            if (idx >= 0 && idx < member.length() - 1) {
                try {
                    total += Double.parseDouble(member.substring(idx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private static Double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Windows(long count5m, long count1h, long count24h, double value1h, double value24h) {
    }
}
