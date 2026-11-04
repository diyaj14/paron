package org.paron.syncservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.paron.syncservice.dto.SyncRequest;
import org.paron.syncservice.dto.SyncResponse;
import org.paron.syncservice.kafka.TransactionProducer;
import org.paron.syncservice.exception.SyncException;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.signature.SignatureVerifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncService {

    private final OfflineTransactionRepository transactionRepository;
    private final TransactionProducer transactionProducer;
    private final SignatureVerifier signatureVerifier;
    private static final int MAX_BATCH_SIZE = 100;

    public SyncResponse submitTransactions(SyncRequest request, String userId) {

        if (request.getTransactions().size() > MAX_BATCH_SIZE) {
            throw new SyncException("BATCH_TOO_LARGE", "Maximum " + MAX_BATCH_SIZE + " transactions per sync request. " +
                    "Received: " + request.getTransactions().size());
        }

        List<String> acceptedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        for (OfflineTransactionDto txn : request.getTransactions()) {
            try {
                txn.setUserId(userId);

                // Signed-receipt gate (Step 0b): a transaction that cannot
                // prove it was signed by its device key is either forged or
                // tampered with — refuse it before it ever reaches Kafka.
                if (!signatureVerifier.isValid(txn)) {
                    failedIds.add(txn.getDeviceTransactionId());
                    log.warn("Refusing transaction: signature invalid. deviceTransactionId={}",
                            txn.getDeviceTransactionId());
                    continue;
                }

                transactionProducer.publish(txn);
                acceptedIds.add(txn.getDeviceTransactionId());
                log.debug("Published to Kafka. deviceTransactionId={}, userId={}",
                        txn.getDeviceTransactionId(), userId);
            } catch (Exception e) {
                log.error("Failed to publish transaction to Kafka. deviceTransactionId={}",
                        txn.getDeviceTransactionId(), e);
                failedIds.add(txn.getDeviceTransactionId());
            }
        }
        log.info("Sync request complete. accepted={}, failed={}",
                acceptedIds.size(), failedIds.size());

        String message = failedIds.isEmpty()
                ? "All transactions accepted and queued for settlement"
                : acceptedIds.size() + " accepted, " + failedIds.size() +
                  " failed to queue (retry those on next sync)";

        return SyncResponse.builder()
                .message(message)
                .acceptedCount(acceptedIds.size())
                .acceptedDeviceTransactionIds(acceptedIds)
                .build();
    }

    public List<OfflineTransaction> getStatusForUser(String userId) {
        log.debug("Fetching transaction history for userId={}", userId);
        return transactionRepository.findByUserIdOrderByReceivedAtDesc(userId);
    }

    public List<OfflineTransaction> getAllTransactions() {
        log.debug("Fetching all sync transactions for admin risk audit");
        return transactionRepository.findAllByOrderByReceivedAtDesc();
    }
}
