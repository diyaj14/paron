package org.paron.syncservice.batch;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.client.LedgerServiceClient;
import org.paron.syncservice.client.TokenServiceClient;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.service.IdempotencyService;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/*
 * The final step — actually moves money and persists the outcome.
 *
 * Spring Batch calls write() once per CHUNK (a batch of items, sized by
 * SettlementJobConfig's chunk size), not once per item. This is the key
 * efficiency idea behind Spring Batch: reading happens one at a time,
 * but writing/committing happens in controlled groups, which is much
 * gentler on the database than committing every single row individually.
 *
 * For each item in the chunk:
 *   - If it was already REJECTED/FAILED by the processor, just save that
 *     outcome (no ledger call needed — nothing to settle).
 *   - If it was approved (status=PROCESSING, has a reservationId), call
 *     ledger-service's /settle, then mark the token as used, then save
 *     the transaction as SETTLED.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SettlementWriter implements ItemWriter<SettlementContext> {

    private final LedgerServiceClient ledgerServiceClient;
    private final TokenServiceClient tokenServiceClient;
    private final OfflineTransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    @Override
    public void write(Chunk<? extends SettlementContext> chunk) {
        for (SettlementContext context : chunk) {
            OfflineTransaction transaction = context.getTransaction();

            // Already rejected or failed during processing — nothing to
            // settle, just persist the outcome the processor decided on.
            if (context.getReservationID() == null) {
                transactionRepository.save(transaction);
                log.info("Saved non-settled transaction. status={}, deviceTransactionId={}",
                        transaction.getStatus(), transaction.getDeviceTransactionId());
                continue;
            }
            try {
                // ── Actually move the money ──────────────────────────────────
                ledgerServiceClient.settle(context.getReservationID(), transaction.getAmount());

                // ── Credit the merchant for this payment (best-effort) ───────
                // The customer side has already settled; if this fails we log it
                // instead of failing the whole transaction — the merchant can be
                // reconciled manually.
                if (transaction.getMerchantId() != null && !transaction.getMerchantId().isBlank()) {
                    try {
                        ledgerServiceClient.creditMerchant(transaction.getMerchantId(), transaction.getAmount());
                    } catch (Exception creditEx) {
                        log.error("Merchant credit failed after successful settle. merchantId={}, deviceTransactionId={}",
                                transaction.getMerchantId(), transaction.getDeviceTransactionId(), creditEx);
                    }
                }

                // ── Tell token-service this token is now spent ──────────────
                // Separate try: if settle() succeeded but markAsUsed() fails,
                // we MUST NOT retry settle() — that would double-debit.
                // The money is already moved; a stale token is a lesser problem
                // that token-service can reconcile independently.
                try {
                    tokenServiceClient.markAsUsed(transaction.getOfflineToken(), transaction.getAmount());
                } catch (Exception markEx) {
                    log.error("Token markAsUsed failed after successful settle. " +
                                    "Money moved but token not marked spent. deviceTransactionId={}",
                            transaction.getDeviceTransactionId(), markEx);
                }

                transaction.setStatus(TransactionStatus.SETTLED);
                transaction.setSettledAt(LocalDateTime.now());
                transactionRepository.save(transaction);

                log.info("Transaction settled successfully. deviceTransactionId={}, amount={}",
                        transaction.getDeviceTransactionId(), transaction.getAmount());

            } catch (Exception e) {
                // ledger-service call failed even after retries/circuit breaker
                // fallback. Mark FAILED so the next scheduled job run picks
                // this transaction up again (see SettlementJobConfig's reader,
                // which only selects RECEIVED status — so we reset back to
                // RECEIVED here rather than leaving it stuck on PROCESSING).
                log.error("Settlement failed for deviceTransactionId={}. Will retry next run.",
                        transaction.getDeviceTransactionId(), e);
                idempotencyService.releaseClaim(transaction.getDeviceTransactionId());
                transaction.setStatus(TransactionStatus.RECEIVED);
                transaction.setRejectionReason("SETTLEMENT_FAILED: " + e.getMessage());
                transactionRepository.save(transaction);
            }
        }
    }
}
