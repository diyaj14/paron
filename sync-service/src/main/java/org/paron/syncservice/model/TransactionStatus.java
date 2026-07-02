package org.paron.syncservice.model;

/*
        * Lifecycle of a single offline transaction as it moves through sync-service.
 *
         * RECEIVED   — arrived from the device, pushed to Kafka, not yet processed
 * PROCESSING — picked up by the Spring Batch job, currently being validated/settled
 * SETTLED    — successfully validated, fraud-checked, and debited via ledger-service
 * REJECTED   — failed validation (bad token, fraud flag, or amount mismatch)
 * FAILED     — settlement itself failed (e.g. ledger-service was unreachable);
 *              eligible for retry
 */
public enum TransactionStatus {
    RECEIVED,
    PROCESSING,
    SETTLED,
    REJECTED,
    FAILED
}
