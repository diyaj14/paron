package org.paron.syncservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.service.IdempotencyService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/*picks transaction from kafka producer, saves it in database with
status=recieved,after token validation,fraud check,debiting
ledger
(spring batch job-settlementJobConfig)

 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionConsumer {

    private final TransactionProducer producer;
    private final OfflineTransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    @KafkaListener(
            topics = "${spring.kafka.topic.offline-transactions}",
            groupId = "${spring.kafka.consumer.group-id}"
    )

    public void consume(OfflineTransactionDto dto) {
        log.info("Received from Kafka:device transactionId= {}", dto.getDeviceTransactionId());
        // First idempotency checkpoint — fast Redis lookup, before we even
        // touch Postgres. See IdempotencyService for the full explanation
        // of why we check in two places (Redis AND the DB unique constraint).
        if (idempotencyService.isAlreadyProcessed(dto.getDeviceTransactionId())) {
            log.warn("Duplicate transaction detected at Kafka consumer stage, skipping. deviceTransactionId={}",
                    dto.getDeviceTransactionId());
            return;   // silently skip — this is expected behavior for a retry, not an error
        }

        OfflineTransaction transaction = OfflineTransaction.builder()
                .deviceTransactionId(dto.getDeviceTransactionId())
                .userId(extractUserIdPlaceholder(dto))   // see note below
                .offlineToken(dto.getOfflineToken())
                .amount(dto.getAmount())
                .merchantId(dto.getMerchantId())
                .transactedAt(dto.getTransactedAt())
                .status(TransactionStatus.RECEIVED)
                .build();
        try {
            transactionRepository.save(transaction);
            log.info("Transaction saved with RECEIVED status. deviceTransactionId={}",
                    dto.getDeviceTransactionId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The DB's unique constraint on device_transaction_id caught a
            // duplicate that slipped past the Redis check (e.g. Redis was
            // briefly unavailable). This is the "belt and suspenders" backup.
            log.warn("Duplicate transaction caught by DB unique constraint. deviceTransactionId={}",
                    dto.getDeviceTransactionId());
        }
    }
/*
 * Temporary placeholder — userId isn't directly in the DTO sent by the
 * device (the device only sends the offline token, not the raw userId,
 * since the token IS the proof of identity). The real userId gets
 * properly extracted by decoding the JWT in SettlementProcessor during
 * token validation. We store a placeholder here just so the column
 * isn't left null; SettlementProcessor overwrites it with the real
 * value once the token is decoded.
 */
        private String extractUserIdPlaceholder(OfflineTransactionDto dto) {
            return "PENDING_TOKEN_DECODE";
        }
}

