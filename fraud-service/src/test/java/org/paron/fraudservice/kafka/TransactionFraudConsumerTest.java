package org.paron.fraudservice.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.repository.FraudAlertRepository;
import org.paron.fraudservice.service.FraudScoringService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionFraudConsumerTest {

    @Mock
    private FraudScoringService scoringService;

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @InjectMocks
    private TransactionFraudConsumer consumer;

    @Captor
    private ArgumentCaptor<FraudAlert> alertCaptor;

    private TransactionEvent event() {
        TransactionEvent event = new TransactionEvent();
        event.setUserId("user-1");
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    @Test
    void rejectedTransactionSavesAlert() {
        FraudAlertResponse response = FraudAlertResponse.builder()
                .transactionId("txn-1")
                .score(0.8)
                .approved(false)
                .riskLevel(RiskLevel.HIGH)
                .triggeredRules(List.of("AMOUNT_ANOMALY"))
                .build();

        when(scoringService.evaluate(any())).thenReturn(response);

        consumer.onTransaction(event());

        verify(fraudAlertRepository).save(alertCaptor.capture());
        FraudAlert alert = alertCaptor.getValue();

        assertEquals("txn-1", alert.getTransactionId());
        assertEquals("user-1", alert.getUserId());
        assertEquals(0, new BigDecimal("100.00").compareTo(alert.getAmount()));
        assertEquals(0.8, alert.getRiskScore(), 0.001);
        assertEquals(RiskLevel.HIGH, alert.getRiskLevel());
        assertEquals("AMOUNT_ANOMALY", alert.getTriggeredRules());
        assertEquals("PENDING", alert.getStatus());
        assertNotNull(alert.getCreatedAt());
    }

    @Test
    void multipleTriggeredRulesJoinedByComma() {
        FraudAlertResponse response = FraudAlertResponse.builder()
                .transactionId("txn-1")
                .score(0.9)
                .approved(false)
                .riskLevel(RiskLevel.CRITICAL)
                .triggeredRules(List.of("AMOUNT_ANOMALY", "VELOCITY"))
                .build();

        when(scoringService.evaluate(any())).thenReturn(response);

        consumer.onTransaction(event());

        verify(fraudAlertRepository).save(alertCaptor.capture());
        assertEquals("AMOUNT_ANOMALY,VELOCITY", alertCaptor.getValue().getTriggeredRules());
    }

    @Test
    void approvedTransactionDoesNotSaveAlert() {
        FraudAlertResponse response = FraudAlertResponse.builder()
                .transactionId("txn-1")
                .score(0.0)
                .approved(true)
                .riskLevel(RiskLevel.LOW)
                .triggeredRules(List.of())
                .build();

        when(scoringService.evaluate(any())).thenReturn(response);

        consumer.onTransaction(event());

        verify(fraudAlertRepository, never()).save(any());
    }
}
