package org.paron.fraudservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.repository.FraudAlertRepository;
import org.paron.fraudservice.service.FraudScoringService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionFraudConsumer {

    private final FraudScoringService scoringService;
    private final FraudAlertRepository fraudAlertRepository;

    @KafkaListener(topics = "offline.transactions", groupId = "fraud-service-group")
    public void onTransaction(TransactionEvent event) {
        FraudAlertResponse response = scoringService.evaluate(event);

        if (!response.isApproved()) {
            FraudAlert alert = FraudAlert.builder()
                    .transactionId(event.getDeviceTransactionId())
                    .userId(event.getUserId())
                    .amount(event.getAmount())
                    .riskScore(response.getScore())
                    .riskLevel(response.getRiskLevel())
                    .triggeredRules(String.join(",", response.getTriggeredRules()))
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            fraudAlertRepository.save(alert);
            log.warn("fraud alert saved userId={},score={},rules={}",
                    event.getUserId(), response.getScore(), response.getTriggeredRules());
        } else {
            log.debug("transaction approved userId={},score={}", event.getUserId(), response.getScore());
        }
    }
}
