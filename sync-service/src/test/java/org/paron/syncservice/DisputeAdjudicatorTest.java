package org.paron.syncservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.syncservice.client.TokenServiceClient;
import org.paron.syncservice.dto.TokenSpendState;
import org.paron.syncservice.dto.adjudicate.AdjudicateRequest;
import org.paron.syncservice.dto.adjudicate.AdjudicationResponse;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.service.DisputeAdjudicator;
import org.paron.syncservice.service.DisputeReasoner;
import org.paron.syncservice.signature.SignatureVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/*
 * Unit tests for the deterministic rule tree in DisputeAdjudicator. These
 * cover the five rulings the AI judge can hand down:
 *
 *   FORGED_RECEIPT        one receipt fails cryptographic re-verification
 *   DOUBLE_SPEND          both settled but jointly over the reserved cap
 *   SINGLE_PAYMENT        same payment seen twice / only one claim settled
 *   MULTIPLE_LEGITIMATE   both genuine, jointly within the cap
 *   INSUFFICIENT_EVIDENCE receipts not found / token-state unavailable
 *
 * SignatureVerifier, TokenServiceClient and DisputeReasoner are mocked so we
 * test ONLY the ruling logic, keeping it deterministic (as designed).
 */
@ExtendWith(MockitoExtension.class)
class DisputeAdjudicatorTest {

    @Mock
    private OfflineTransactionRepository transactionRepository;
    @Mock
    private SignatureVerifier signatureVerifier;
    @Mock
    private TokenServiceClient tokenServiceClient;
    @Mock
    private DisputeReasoner reasoner;

    @InjectMocks
    private DisputeAdjudicator adjudicator;

    private static final String TOKEN = "header.payload.sig";

    private OfflineTransaction txn(String id, BigDecimal amount, String device,
                                   LocalDateTime at, TransactionStatus status) {
        return OfflineTransaction.builder()
                .deviceTransactionId(id)
                .offlineToken(TOKEN)
                .amount(amount)
                .merchantId("merchant_abc")
                .deviceId(device)
                .signature("sig-" + id)
                .publicKey("jwk-" + id)
                .transactedAt(at)
                .status(status)
                .build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(reasoner.summarize(anyString(), anyString(), anyList()))
                .thenReturn("template summary");
    }

    private void bothSettled(LocalDateTime atA, LocalDateTime atB) {
        when(transactionRepository.findByDeviceTransactionId("A"))
                .thenReturn(Optional.of(txn("A", new BigDecimal("100"), "dev-1", atA, TransactionStatus.SETTLED)));
        when(transactionRepository.findByDeviceTransactionId("B"))
                .thenReturn(Optional.of(txn("B", new BigDecimal("100"), "dev-1", atB, TransactionStatus.SETTLED)));
    }

    private AdjudicationResponse run(String... ids) {
        AdjudicateRequest request = new AdjudicateRequest();
        request.setDeviceTransactionIds(List.of(ids));
        return adjudicator.adjudicate(request);
    }

    @Test
    void forgedReceipt_shouldRuleForged_andNameTheForgedParty() {
        bothSettled(LocalDateTime.of(2026,8,30,10,0), LocalDateTime.of(2026,8,30,10,5));
        lenient().when(signatureVerifier.isValid(any(OfflineTransaction.class)))
                .thenAnswer(inv -> {
                    OfflineTransaction t = inv.getArgument(0);
                    return "A".equals(t.getDeviceTransactionId()); // B is forged
                });
        when(tokenServiceClient.getSpendState(TOKEN)).thenReturn(spendState(200, 100));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("FORGED_RECEIPT");
        assertThat(response.getWinnerDeviceTransactionId()).isEqualTo("A");
        assertThat(response.getLoserDeviceTransactionId()).isEqualTo("B");
        assertThat(response.isBinding()).isTrue();
    }

    @Test
    void reconciledSamePayment_shouldRuleSinglePayment() {
        bothSettled(LocalDateTime.of(2026,8,30,10,0), LocalDateTime.of(2026,8,30,10,0));
        when(signatureVerifier.isValid(any(OfflineTransaction.class))).thenReturn(true);
        when(tokenServiceClient.getSpendState(TOKEN)).thenReturn(spendState(100, 100));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("SINGLE_PAYMENT");
        assertThat(response.getWinnerDeviceTransactionId()).isEqualTo("A");
        assertThat(response.isBinding()).isTrue();
    }

    @Test
    void overspent_shouldRuleDoubleSpend_earlierWins() {
        // Same device, two genuinely-signed payments of ₹100 each, cap is ₹150.
        bothSettled(LocalDateTime.of(2026,8,30,10,0), LocalDateTime.of(2026,8,30,10,5));
        when(signatureVerifier.isValid(any(OfflineTransaction.class))).thenReturn(true);
        when(tokenServiceClient.getSpendState(TOKEN)).thenReturn(spendState(200, 150));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("DOUBLE_SPEND");
        assertThat(response.getWinnerDeviceTransactionId()).isEqualTo("A"); // earlier
        assertThat(response.getLoserDeviceTransactionId()).isEqualTo("B");
        assertThat(response.isBinding()).isTrue();
    }

    @Test
    void bothGenuineWithinCap_shouldRuleMultipleLegitimate() {
        bothSettled(LocalDateTime.of(2026,8,30,10,0), LocalDateTime.of(2026,8,30,10,5));
        when(signatureVerifier.isValid(any(OfflineTransaction.class))).thenReturn(true);
        when(tokenServiceClient.getSpendState(TOKEN)).thenReturn(spendState(200, 300));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("MULTIPLE_LEGITIMATE");
        assertThat(response.getWinnerDeviceTransactionId()).isNull();
        assertThat(response.isBinding()).isTrue();
    }

    @Test
    void onlyOneSettled_shouldRuleSinglePayment_winnerHolds() {
        // A is settled, B still RECEIVED (never settled). The settled one holds.
        when(transactionRepository.findByDeviceTransactionId("A"))
                .thenReturn(Optional.of(txn("A", new BigDecimal("100"), "dev-1",
                        LocalDateTime.of(2026,8,30,10,0), TransactionStatus.SETTLED)));
        when(transactionRepository.findByDeviceTransactionId("B"))
                .thenReturn(Optional.of(txn("B", new BigDecimal("100"), "dev-1",
                        LocalDateTime.of(2026,8,30,10,0), TransactionStatus.RECEIVED)));
        when(signatureVerifier.isValid(any(OfflineTransaction.class))).thenReturn(true);
        when(tokenServiceClient.getSpendState(TOKEN)).thenReturn(spendState(100, 300));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("SINGLE_PAYMENT");
        assertThat(response.getWinnerDeviceTransactionId()).isEqualTo("A");
        assertThat(response.isBinding()).isTrue();
    }

    @Test
    void tokenStateUnavailable_shouldRuleInsufficientEvidence() {
        bothSettled(LocalDateTime.of(2026,8,30,10,0), LocalDateTime.of(2026,8,30,10,5));
        when(signatureVerifier.isValid(any(OfflineTransaction.class))).thenReturn(true);
        when(tokenServiceClient.getSpendState(TOKEN))
                .thenThrow(new RuntimeException("token-service down"));

        AdjudicationResponse response = run("A", "B");

        assertThat(response.getRuling()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(response.isBinding()).isFalse();
    }

    @Test
    void missingReceipts_shouldRuleInsufficientEvidence() {
        when(transactionRepository.findByDeviceTransactionId(anyString())).thenReturn(Optional.empty());

        AdjudicationResponse response = run("ghost-A", "ghost-B");

        assertThat(response.getRuling()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(response.isBinding()).isFalse();
    }

    private TokenSpendState spendState(int spent, int cap) {
        return spendState(BigDecimal.valueOf(spent), BigDecimal.valueOf(cap));
    }

    private TokenSpendState spendState(BigDecimal spent, BigDecimal cap) {
        return TokenSpendState.builder()
                .maxAmount(cap)
                .spentAmount(spent)
                .status("ACTIVE")
                .build();
    }
}
