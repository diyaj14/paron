package org.paron.syncservice.batch;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.paron.syncservice.model.OfflineTransaction;

/*
 * Carries an OfflineTransaction together with the reservationId that
 * token-service returned during validation, from SettlementProcessor
 * to SettlementWriter.
 *
 * Why this class exists:
 * SettlementProcessor needs to pass the reservationId forward to
 * SettlementWriter (which needs it to call ledger-service's /settle
 * endpoint), but ItemProcessor<I, O> only lets you return ONE object of
 * type O. Rather than storing the reservationId on a mutable instance
 * field of SettlementProcessor (which would be unsafe — Spring Batch
 * beans are singletons, and multiple items can be mid-processing
 * concurrently with multi-threaded steps), we wrap both pieces of data
 * together in this one object and pass the wrapper through the pipeline
 * instead.
 */

@Data
@RequiredArgsConstructor
public class SettlementContext {
    private final OfflineTransaction transaction;
    private final String reservationID; //null if transaction rejected
}
