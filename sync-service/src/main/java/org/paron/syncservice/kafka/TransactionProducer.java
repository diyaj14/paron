package org.paron.syncservice.kafka;

/*put transactions to kafka,called by controller after device's
Http request comes in
 * Why key by userId (see the second argument to .send())?
 * Kafka uses the key to decide which partition a message lands in.
 * Sending all of one user's transactions with the same key guarantees
 * they land in the same partition, which guarantees Kafka processes
 * them in the exact order they were sent — important since transaction
 * order can matter for fraud detection (e.g. velocity checks need to
 * see transactions in chronological order).
 */

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class TransactionProducer {

    private final KafkaTemplate<String, OfflineTransactionDto> kafkaTemplate;

    @Value("${spring.kafka.topic.offline-transactions}")
    private String topicName;
    public void publish(OfflineTransactionDto transaction) {
        log.info("Publishing transaction to Kafka. deviceTransactionId={}, userId not in DTO directly",
                transaction.getDeviceTransactionId());

        // The token itself encodes the userId, but we don't have
        // it decoded here in the producer (that happens later in SettlementProcessor).
        // We key by deviceTransactionId instead
        // still gives us reasonable
        // partition spread, and avoids decoding a JWT just to pick a partition.
        kafkaTemplate.send(topicName, transaction.getDeviceTransactionId(), transaction);
    }




}
