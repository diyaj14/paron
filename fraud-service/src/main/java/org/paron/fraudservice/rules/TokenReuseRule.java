package org.paron.fraudservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.TransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class TokenReuseRule implements FraudRule {

    private final StringRedisTemplate redisTemplate;
    private final long ttlHours;

    public TokenReuseRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.token-reuse-ttl-hours:24}") long ttlHours
    ) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
    }

    @Override
    public double evaluate(TransactionEvent event) {
        String token = event.getOfflineToken();
        String deviceId = event.getDeviceId();

        if (token == null || token.isBlank() || deviceId == null || deviceId.isBlank()) {
            return 0.0;
        }

        String key = "token:" + token;

        String previousDevice = redisTemplate.opsForValue().get(key);

        if (previousDevice == null) {
            // first time seeing this token — store it
            redisTemplate.opsForValue().set(key, deviceId, Duration.ofHours(ttlHours));
            return 0.0;
        }

        if (previousDevice.equals(deviceId)) {
            // same device — could be a retry/duplicate, not fraud
            return 0.0;
        }

        // different device — token reused across devices
        log.warn("token reuse userId={},token={},firstDevice={},currentDevice={}",
                event.getUserId(), token, previousDevice, deviceId);
        return 1.0;
    }

    @Override
    public String name() {
        return "TOKEN_REUSE";
    }
}
