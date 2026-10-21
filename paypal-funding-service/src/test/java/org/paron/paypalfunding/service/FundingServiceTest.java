package org.paron.paypalfunding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.paypalfunding.model.FundingAttempt;
import org.paron.paypalfunding.model.FundingStatus;
import org.paron.paypalfunding.repository.FundingAttemptRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundingServiceTest {
    @Mock 
    FundingAttemptRepository attempts;

    @Mock 
    PaypalClient paypal;

    @Mock 
    LedgerClient ledger;

    @InjectMocks 
    FundingService service;
    
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void capture_recoversFromCompletedPaypalOrderWithoutSecondCapture() throws Exception {
        FundingAttempt attempt = attempt(FundingStatus.PAYER_ACTION_REQUIRED);
        when(attempts.findByPaypalOrderId("ORDER-1")).thenReturn(Optional.of(attempt));
        when(paypal.getOrder("ORDER-1")).thenReturn(json.readTree("""
            {"id":"ORDER-1","status":"COMPLETED","purchase_units":[{"payments":{"captures":[{"id":"CAPTURE-1","status":"COMPLETED","amount":{"value":"10.00","currency_code":"USD"}}]}}]}
            """));

        var result = service.capture("ORDER-1");

        assertThat(result.status()).isEqualTo(FundingStatus.CREDITED);
        verify(paypal, never()).capture(anyString());
        verify(ledger).credit(attempt.getId(), "user-1", new BigDecimal("10.00"));
    }

    @Test
    void capture_duplicateCreditedAttemptDoesNotCallPaypalOrLedger() {
        FundingAttempt attempt = attempt(FundingStatus.CREDITED);
        when(attempts.findByPaypalOrderId("ORDER-1")).thenReturn(Optional.of(attempt));

        var result = service.capture("ORDER-1");

        assertThat(result.status()).isEqualTo(FundingStatus.CREDITED);
        verifyNoInteractions(paypal, ledger);
    }

    @Test
    void capture_amountMismatchMarksAttemptFailedWithoutCreditingWallet() throws Exception {
        FundingAttempt attempt = attempt(FundingStatus.PAYER_ACTION_REQUIRED);
        when(attempts.findByPaypalOrderId("ORDER-1")).thenReturn(Optional.of(attempt));
        when(paypal.getOrder("ORDER-1")).thenReturn(json.readTree("""
            {"id":"ORDER-1","status":"COMPLETED","purchase_units":[{"payments":{"captures":[{"id":"CAPTURE-1","status":"COMPLETED","amount":{"value":"11.00","currency_code":"USD"}}]}}]}
            """));

        assertThatThrownBy(() -> service.capture("ORDER-1")).isInstanceOf(RuntimeException.class);

        assertThat(attempt.getStatus()).isEqualTo(FundingStatus.FAILED);
        verifyNoInteractions(ledger);
    }

    private FundingAttempt attempt(FundingStatus status) {
        return FundingAttempt.builder().id(UUID.randomUUID()).userId("user-1").paypalOrderId("ORDER-1")
                .amount(new BigDecimal("10.00")).currency("USD").status(status).build();
    }
}
