package org.paron.fraudservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class VelocityRule implements FraudRule {

    private final StringRedisTemplate redisTemplate;
    private final int windowSeconds;
    private final int maxCount;

    public VelocityRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.velocity-window-seconds:60}") int windowSeconds,
            @Value("${fraud.rules.velocity-max-count:5}") int maxCount
    ) {
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.maxCount = maxCount;
    }

    @Override
    public double evaluate(TransactionEvent event) {
        String key = "velocity:" + event.getUserId();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count == null || count <= maxCount) {
            return 0.0;
        }

        log.warn("velocity breach userId={},count={},max={}", event.getUserId(), count, maxCount);
        return 1.0;
    }

    @Override
    public String name() {
        return "VELOCITY_BREACH";
    }
}
