package org.paron.syncservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.syncservice.batch.SettlementContext;
import org.paron.syncservice.batch.SettlementProcessor;
import org.paron.syncservice.client.FraudCheckClient;
import org.paron.syncservice.client.TokenServiceClient;
import org.paron.syncservice.dto.FraudCheckResult;
import org.paron.syncservice.dto.TokenValidationResult;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.service.IdempotencyService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SettlementProcessorTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TokenServiceClient tokenServiceClient;

    @Mock
    private FraudCheckClient fraudCheckClient;

    @InjectMocks
    private SettlementProcessor settlementProcessor;

    private OfflineTransaction transaction;

    @BeforeEach
    void setUp(){
        transaction = OfflineTransaction.builder()
                .deviceTransactionId("device-txn-001")
                .offlineToken("eyJfaketoken")
                .amount(new BigDecimal("150.00"))
                .merchantId("merchant_abc")
                .transactedAt(LocalDateTime.now())
                .status(TransactionStatus.RECEIVED)
                .build();

    }

    @Test
    void process_whenDuplicate_shouldReturnNullToFilterOut(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(false);

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result).isNull();
        verify(tokenServiceClient, never()).validateToken(any(), any());
    }

    @Test
    void process_whenToken_Invalid_shouldReject(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(false)
                        .reason("EXPIREDJWTEXCEPTION")
                        .build());

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result).isNotNull();
        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getTransaction().getRejectionReason()).contains("TOKEN_INVALID");
        assertThat(result.getReservationID()).isNull();

        verify(idempotencyService, times(1)).releaseClaim("device-txn-001");
        verify(fraudCheckClient, never()).check(any());
    }

    @Test
    void process_whenFraudRejects_shouldReject(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(true)
                        .userId("user_test_123")
                        .reservationId("res_xyz789")
                        .build());
        when(fraudCheckClient.check(any(OfflineTransaction.class)))
                .thenReturn(FraudCheckResult.builder()
                        .score(0.9)
                        .approved(false)
                        .decision("REJECT")
                        .reason("hard_rule_hit")
                        .reasonCodes(List.of("VELOCITY_ELEVATED"))
                        .build());

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getTransaction().getRejectionReason()).contains("FRAUD");
        assertThat(result.getReservationID()).isNull();
    }

    @Test
    void process_whenFraudHolds_shouldHoldForReview(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(true)
                        .userId("user_test_123")
                        .reservationId("res_xyz789")
                        .build());
        when(fraudCheckClient.check(any(OfflineTransaction.class)))
                .thenReturn(FraudCheckResult.builder()
                        .score(0.55)
                        .approved(false)
                        .decision("HOLD_FOR_REVIEW")
                        .confidence(0.75)
                        .modelVersion("lr-calibrated-v1.0.0")
                        .policyVersion("thresholds-v1")
                        .reasonCodes(List.of("VELOCITY_ELEVATED", "AMOUNT_DEVIATION_HIGH"))
                        .build());

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.HELD_FOR_REVIEW);
        assertThat(result.getTransaction().getRejectionReason()).contains("FRAUD_HOLD");
        assertThat(result.getTransaction().getRejectionReason()).contains("VELOCITY_ELEVATED");
        assertThat(result.getReservationID()).isNull();

        verify(idempotencyService, never()).releaseClaim("device-txn-001");
    }

    @Test
    void process_whenModelUnavailable_shouldHoldForReview(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(true)
                        .userId("user_test_123")
                        .reservationId("res_xyz789")
                        .build());
        when(fraudCheckClient.check(any(OfflineTransaction.class)))
                .thenReturn(FraudCheckResult.builder()
                        .score(0.0)
                        .approved(false)
                        .decision("HOLD_FOR_REVIEW")
                        .reason("model_unavailable")
                        .build());

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.HELD_FOR_REVIEW);
        assertThat(result.getTransaction().getRejectionReason()).contains("FRAUD_HOLD");
        assertThat(result.getReservationID()).isNull();
    }

    @Test
    void process_whenAllChecksPass_shouldApprovewithReservationId(){
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(true)
                        .userId("user_test_123")
                        .reservationId("res_xyz789")
                        .build());
        when(fraudCheckClient.check(any(OfflineTransaction.class)))
                .thenReturn(FraudCheckResult.builder()
                        .score(0.1)
                        .approved(true)
                        .decision("APPROVE")
                        .confidence(0.95)
                        .modelVersion("lr-calibrated-v1.0.0")
                        .build());

        SettlementContext result = settlementProcessor.process(transaction);

        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(result.getTransaction().getUserId()).isEqualTo("user_test_123");
        assertThat(result.getReservationID()).isEqualTo("res_xyz789");

        verify(idempotencyService, never()).releaseClaim(anyString());
    }

}
