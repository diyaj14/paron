package org.paron.syncservice.client;

import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.FraudCheckResult;
import org.paron.syncservice.model.OfflineTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/* This class exists specifically so SettlementProcessor never needs to
 * change when fraud-service is eventually built. SettlementProcessor
 * calls check(transaction) and gets back a FraudCheckResult — exactly
 * the same shape a real HTTP call to fraud-service would return.
        * Swapping this implementation for a RestTemplate call later is a
 * one-class change, not a redesign of the settlement pipeline.
        *
 * The one rule implemented here for now: reject any single offline
 * transaction over ₹5,000 as suspicious, since our token-service already
 * caps offline reservations at ₹10,000 total — a single transaction
 * that large within one offline session is an anomaly worth a closer look.
 */
@Component
@Slf4j
public class FraudCheckClient {
    private static final BigDecimal SINGLE_TXN_THRESHOLD = new BigDecimal("5000.00");

    public FraudCheckResult check(OfflineTransaction transaction) {
        if (transaction.getAmount().compareTo(SINGLE_TXN_THRESHOLD) > 0) {
            log.warn("Fraud check flagged large single transaction. amount={}, deviceTransactionId={}",
                    transaction.getAmount(), transaction.getDeviceTransactionId());
            return FraudCheckResult.builder()
                    .score(0.8)
                    .approved(false)
                    .reason("SINGLE_TRANSACTION_AMOUNT_ANOMALY")
                    .build();
        }

        return FraudCheckResult.builder()
                .score(0.1)
                .approved(true)
                .build();
    }
}
