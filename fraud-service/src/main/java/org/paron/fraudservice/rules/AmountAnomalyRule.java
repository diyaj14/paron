package org.paron.fraudservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class AmountAnomalyRule implements FraudRule {

    private static final Duration KEY_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final double multiplier;
    private final long minTransactions;

    public AmountAnomalyRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.amount-anomaly-multiplier:3.0}") double multiplier,
            @Value("${fraud.rules.amount-anomaly-min-transactions:5}") long minTransactions
    ) {
        this.redisTemplate = redisTemplate;
        this.multiplier = multiplier;
        this.minTransactions = minTransactions;
    }

    @Override
    public double evaluate(TransactionEvent event) {
        String totalKey = "amt:total:" + event.getUserId();
        String countKey = "amt:count:" + event.getUserId();

        Double currentTotal = redisTemplate.opsForValue().increment(totalKey, event.getAmount().doubleValue());
        redisTemplate.expire(totalKey, KEY_TTL);

        Long count = redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, KEY_TTL);

        if (count == null || count < minTransactions) {
            return 0.0;
        }

        double average = currentTotal / count;

        if (event.getAmount().doubleValue() > average * multiplier) {
            log.warn("amount anomaly userId={},amount={},avg={},threshold={}",
                    event.getUserId(), event.getAmount(), average, average * multiplier);
            return 1.0;
        }

        return 0.0;
    }

    @Override
    public String name() {
        return "AMOUNT_ANOMALY";
    }
}
