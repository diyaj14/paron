package org.paron.fraudservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TimePatternRule implements FraudRule {

    private static final Duration KEY_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final long minTransactions;

    public TimePatternRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.time-pattern-min-transactions:10}") long minTransactions
    ) {
        this.redisTemplate = redisTemplate;
        this.minTransactions = minTransactions;
    }

    @Override
    public double evaluate(TransactionEvent event) {
        int hour = parseHour(event.getTransactedAt());

        String hourKey = "time:hours:" + event.getUserId();
        String countKey = "time:count:" + event.getUserId();

        redisTemplate.opsForZSet().incrementScore(hourKey, String.valueOf(hour), 1);
        redisTemplate.expire(hourKey, KEY_TTL);

        Long totalCount = redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, KEY_TTL);

        if (totalCount == null || totalCount < minTransactions) {
            return 0.0;
        }

        Double score = redisTemplate.opsForZSet().score(hourKey, String.valueOf(hour));

        if (score == null || score == 0.0) {
            log.warn("time pattern breach userId={},hour={},totalTxns={}",
                    event.getUserId(), hour, totalCount);
            return 0.7;
        }

        return 0.0;
    }

    private int parseHour(String transactedAt) {
        if (transactedAt == null || transactedAt.isBlank()) {
            return LocalDateTime.now().getHour();
        }
        try {
            return LocalDateTime.parse(transactedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME).getHour();
        } catch (Exception e) {
            return LocalDateTime.now().getHour();
        }
    }

    @Override
    public String name() {
        return "TIME_PATTERN";
    }
}
