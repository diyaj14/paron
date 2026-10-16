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
        // GIVEN — idempotency claim fails (already processed elsewhere)
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(false);

        //when
        SettlementContext result = settlementProcessor.process(transaction);

        //THEN- filtered out, never reaches token validation
        assertThat(result).isNull();
        verify(tokenServiceClient, never()).validateToken(any(), any());
    }

    @Test
    void process_whenToken_Invalid_shouldReject(){
        // GIVEN — claim succeeds, but token validation fails
        when(idempotencyService.markAsProcessed("device-txn-001")).thenReturn(true);
        when(tokenServiceClient.validateToken(anyString(), any(BigDecimal.class)))
                .thenReturn(TokenValidationResult.builder()
                        .valid(false)
                        .reason("EXPIREDJWTEXCEPTION")
                        .build());

        // WHEN
        SettlementContext result = settlementProcessor.process(transaction);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getTransaction().getRejectionReason()).contains("TOKEN_INVALID");
        assertThat(result.getReservationID()).isNull();

        // Claim should be released so it can be retried
        verify(idempotencyService, times(1)).releaseClaim("device-txn-001");
        // Fraud check should never run if token is already invalid
        verify(fraudCheckClient, never()).check(any());
    }

    @Test
    void process_whenFraudRejects_shouldReject(){
        // GIVEN — token is valid, but fraud check rejects
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
                        .reason("SINGLE_TRANSACTION_AMOUNT_ANOMALY")
                        .build());

        // WHEN
        SettlementContext result = settlementProcessor.process(transaction);

        // THEN
        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getTransaction().getRejectionReason()).contains("FRAUD");
        assertThat(result.getReservationID()).isNull();
    }

    @Test
    void process_whenAllChecksPass_shouldApprovewithReservationId(){
        // GIVEN — everything passes
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
                        .build());

        // WHEN
        SettlementContext result = settlementProcessor.process(transaction);

        // THEN
        assertThat(result.getTransaction().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(result.getTransaction().getUserId()).isEqualTo("user_test_123");
        assertThat(result.getReservationID()).isEqualTo("res_xyz789");

        // Claim should NOT be released — this transaction is proceeding to settlement
        verify(idempotencyService, never()).releaseClaim(anyString());
    }

}
