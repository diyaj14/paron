package org.paron.syncservice.service;


import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/*to ensure that no device is able to send duplicate transaction
 *
 * Why this matters more than almost anything else in the system:
 * if a device's network blips mid-sync, it might retry sending the same
 * batch of transactions. Without this check, a single ₹150 payment could
 * get debited from the user's account two, three, or more times.
 *
 * How it works:
 * Every transaction carries a deviceTransactionId, generated ON THE
 * DEVICE at the moment of payment — not by our backend. This means the
 * SAME transaction always carries the SAME id, no matter how many times
 * it gets resent. We use Redis as a fast "have I seen this id before?"
 * lookup, since checking Redis is much faster than querying Postgres for
 * every single incoming transaction.
 *
 * markAsProcessed() uses a SET-IF-NOT-EXISTS operation (setIfAbsent) —
 * this is important. If two requests for the exact same transaction
 * arrive at almost the same instant, setIfAbsent guarantees only ONE of
 * them successfully "claims" the id; the other gets back false and
 * backs off. This is the same kind of race-condition protection as the
 * pessimistic database lock in ledger-service, just implemented at the
 * Redis layer instead.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    @Value("${idempotency.key-prefix:idempotency:txn:}")
    private String keyPrefix;

    @Value("${idempotency.ttl-hours:48}")
    private long ttlHours;

    /*
     * Fast check — has this transaction already been processed?
     * Used by TransactionConsumer before even saving to the database.
     */
    public boolean isAlreadyProcessed(String deviceTransactionId) {
        String key = buildKey(deviceTransactionId);
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /*
     * Atomically claims this transaction ID as "being processed".
     *
     * Returns true  — this caller successfully claimed it (proceed with settlement)
     * Returns false — someone else already claimed it (back off, skip)
     *
     * Called by SettlementProcessor right before calling ledger-service,
     * not earlier — we want the "claim" to happen as close as possible
     * to the actual money-moving operation, to minimize any window where
     * a duplicate could sneak through between the check and the claim.
     */
    public boolean markAsProcessed(String deviceTransactionId) {
        String key = buildKey(deviceTransactionId);

        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(ttlHours));

        boolean claimed = Boolean.TRUE.equals(wasSet);

        if (!claimed) {
            log.warn("Transaction already claimed by another process. deviceTransactionId={}",
                    deviceTransactionId);
        }

        return claimed;
    }
    /*release a claim if settlement fails(ledger rejection) due to buiness reason
    Only call this for FAILURES that should allow a retry, never for
     * successful settlements — successful settlements should stay
    */
    public void releaseClaim(String deviceTransactionId) {
        String key = buildKey(deviceTransactionId);
        redisTemplate.delete(key);
        log.info("Released idempotency claim for retry. deviceTransactionId={}", deviceTransactionId);
    }

    private String buildKey(String deviceTransactionId) {
        return keyPrefix + deviceTransactionId;
    }


}
