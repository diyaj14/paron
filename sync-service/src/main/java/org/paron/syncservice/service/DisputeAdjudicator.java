package org.paron.syncservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.client.TokenServiceClient;
import org.paron.syncservice.dto.TokenSpendState;
import org.paron.syncservice.dto.adjudicate.AdjudicateRequest;
import org.paron.syncservice.dto.adjudicate.AdjudicationResponse;
import org.paron.syncservice.dto.adjudicate.EvidenceCheck;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.signature.SignatureVerifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * The "AI judge" — autonomous dispute triaging.
 *
 * When two offline receipts contradict each other after reconnection (a
 * merchant claims ₹200, the customer's device says it already paid; or the
 * same token was spent from two devices), this component arbitrates who
 * holds the valid claim. It works like a real judge's clerk:
 *
 *   1. GATHER EVIDENCE  — load both receipts, re-verify their signatures
 *                         (cryptographic proof of who really created them),
 *                         check each one's settlement status, and pull the
 *                         token's authoritative spend state from token-service.
 *   2. APPLY THE RULES  — a small deterministic rule tree maps the evidence
 *                         to a binding ruling. This is deliberate: money
 *                         decisions must never be a coin flip or an LLM
 *                         hallucination. The LLM may only write the
 *                         human-readable summary, never decide.
 *   3. ISSUE A VERDICT  — structured judgement card with per-check evidence
 *                         and confidence, so every ruling is auditable.
 *
 * The rules (priority order):
 *   - receipts must reference the SAME token            -> else INSUFFICIENT
 *   - a receipt failing signature check is a FORGED     -> FORGED_RECEIPT
 *   - identical amount + same device + same timestamp   -> SINGLE_PAYMENT
 *   - both settled AND token over its cap               -> DOUBLE_SPEND,
 *     the earlier payment wins, the later one is reclaimed
 *   - both settled and within cap                       -> MULTIPLE_LEGITIMATE
 *   - only one settled -> the settled receipt wins      -> SINGLE_PAYMENT
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeAdjudicator {

    private final OfflineTransactionRepository transactionRepository;
    private final SignatureVerifier signatureVerifier;
    private final TokenServiceClient tokenServiceClient;
    private final DisputeReasoner reasoner;

    public AdjudicationResponse adjudicate(AdjudicateRequest request) {
        String disputeId = UUID.randomUUID().toString();

        List<OfflineTransaction> receipts = new ArrayList<>();
        for (String id : request.getDeviceTransactionIds()) {
            transactionRepository.findByDeviceTransactionId(id).ifPresent(receipts::add);
        }

        if (receipts.size() != 2) {
            return insufficient(disputeId, receipts.size(), request.getDeviceTransactionIds());
        }

        OfflineTransaction a = receipts.get(0);
        OfflineTransaction b = receipts.get(1);
        List<EvidenceCheck> evidence = new ArrayList<>();

        // ── 1. GATHER EVIDENCE ──────────────────────────────────────────
        boolean sigA = signatureVerifier.isValid(a);
        evidence.add(EvidenceCheck.builder()
                .check("SIGNATURE_TXN_A")
                .passed(sigA)
                .detail(a.getDeviceTransactionId() + (sigA ? " signature verified" : " signature INVALID — forged or tampered"))
                .build());
        boolean sigB = signatureVerifier.isValid(b);
        evidence.add(EvidenceCheck.builder()
                .check("SIGNATURE_TXN_B")
                .passed(sigB)
                .detail(b.getDeviceTransactionId() + (sigB ? " signature verified" : " signature INVALID — forged or tampered"))
                .build());

        boolean settledA = a.getStatus() == TransactionStatus.SETTLED;
        boolean settledB = b.getStatus() == TransactionStatus.SETTLED;
        evidence.add(EvidenceCheck.builder().check("SETTLED_TXN_A").passed(settledA)
                .detail("status=" + a.getStatus()).build());
        evidence.add(EvidenceCheck.builder().check("SETTLED_TXN_B").passed(settledB)
                .detail("status=" + b.getStatus()).build());

        boolean sameToken = same(a.getOfflineToken(), b.getOfflineToken());
        evidence.add(EvidenceCheck.builder().check("SAME_TOKEN").passed(sameToken)
                .detail(sameToken ? "both receipts reference the same offline token"
                        : "receipts reference different tokens — not comparable")
                .build());

        TokenSpendState tokenState = null;
        String token = a.getOfflineToken();
        if (sameToken) {
            try {
                tokenState = tokenServiceClient.getSpendState(token);
            } catch (Exception e) {
                log.warn("Token-state evidence unavailable for dispute {}. error={}", disputeId, e.getMessage());
            }
        }
        boolean tokenConnective = tokenState != null;
        evidence.add(EvidenceCheck.builder().check("TOKEN_STATE_AVAILABLE").passed(tokenConnective)
                .detail(tokenState != null
                        ? "spent=" + tokenState.getSpentAmount() + ", cap=" + tokenState.getMaxAmount() + ", status=" + tokenState.getStatus()
                        : "token-service /state unavailable").build());

        boolean overspent = tokenState != null
                && tokenState.getSpentAmount() != null
                && tokenState.getMaxAmount() != null
                && tokenState.getSpentAmount().compareTo(tokenState.getMaxAmount()) > 0;
        evidence.add(EvidenceCheck.builder().check("TOKEN_OVER_CAP").passed(overspent)
                .detail(overspent ? "jointly spent MORE than the reserved cap — at least one claim is dishonest"
                        : "spend is within the reserved cap").build());

        boolean sameDevice = same(a.getDeviceId(), b.getDeviceId());
        boolean sameAmount = a.getAmount() != null && a.getAmount().compareTo(b.getAmount()) == 0;
        boolean sameMoment = a.getTransactedAt() != null && b.getTransactedAt() != null
                && Math.abs(ChronoUnit.SECONDS.between(a.getTransactedAt(), b.getTransactedAt())) <= 2;

        // ── 2. APPLY THE RULES ─────────────────────────────────────────
        String ruling;
        String winner = null;
        String loser = null;

        if (!sameToken || !tokenConnective) {
            ruling = "INSUFFICIENT_EVIDENCE";
        } else if (!sigA || !sigB) {
            ruling = "FORGED_RECEIPT";
            if (sigA && !sigB) { winner = a.getDeviceTransactionId(); loser = b.getDeviceTransactionId(); }
            if (!sigA && sigB) { winner = b.getDeviceTransactionId(); loser = a.getDeviceTransactionId(); }
        } else if (sameDevice && sameAmount && sameMoment) {
            ruling = "SINGLE_PAYMENT";
            winner = a.getDeviceTransactionId();
            loser = b.getDeviceTransactionId();
        } else if (settledA && settledB && overspent) {
            ruling = "DOUBLE_SPEND";
            OfflineTransaction earlier = sameMoment ? a : (a.getTransactedAt().isBefore(b.getTransactedAt()) ? a : b);
            OfflineTransaction later = earlier == a ? b : a;
            winner = earlier.getDeviceTransactionId();
            loser = later.getDeviceTransactionId();
        } else if (settledA && settledB) {
            ruling = "MULTIPLE_LEGITIMATE";
        } else if (settledA != settledB) {
            ruling = "SINGLE_PAYMENT";
            winner = settledA ? a.getDeviceTransactionId() : b.getDeviceTransactionId();
            loser = settledA ? b.getDeviceTransactionId() : a.getDeviceTransactionId();
        } else {
            ruling = "INSUFFICIENT_EVIDENCE";
        }

        // ── 3. ISSUE THE VERDICT ───────────────────────────────────────
        double confidence = clamp01((double) evidence.stream().filter(EvidenceCheck::isPassed).count() / evidence.size());
        BigDecimal amountInQuestion = sumAmounts(a, b);

        String summary = reasoner.summarize(ruling, winner, evidence);

        log.info("Adjudication complete. disputeId={}, ruling={}, winner={}, confidence={}",
                disputeId, ruling, winner, confidence);

        return AdjudicationResponse.builder()
                .disputeId(disputeId)
                .ruling(ruling)
                .winnerDeviceTransactionId(winner)
                .loserDeviceTransactionId(loser)
                .amountInQuestion(amountInQuestion)
                .confidence(confidence)
                .evidence(evidence)
                .summary(summary)
                .binding(!"INSUFFICIENT_EVIDENCE".equals(ruling))
                .adjudicatedAt(LocalDateTime.now())
                .build();
    }

    private AdjudicationResponse insufficient(String disputeId, int found, List<String> requested) {
        double confidence = found > 0 ? 0.25 : 0.0;
        return AdjudicationResponse.builder()
                .disputeId(disputeId)
                .ruling("INSUFFICIENT_EVIDENCE")
                .amountInQuestion(BigDecimal.ZERO)
                .confidence(confidence)
                .evidence(List.of(EvidenceCheck.builder()
                        .check("RECEIPTS_FOUND")
                        .passed(false)
                        .detail("loaded " + found + "/" + requested.size() + " receipts: " + requested)
                        .build()))
                .summary("The judge could not rule: the receipts could not both be loaded for verification. No money is moved and no claim is granted.")
                .binding(false)
                .adjudicatedAt(LocalDateTime.now())
                .build();
    }

    private BigDecimal sumAmounts(OfflineTransaction a, OfflineTransaction b) {
        BigDecimal sum = BigDecimal.ZERO;
        if (a.getAmount() != null) sum = sum.add(a.getAmount());
        if (b.getAmount() != null) sum = sum.add(b.getAmount());
        return sum;
    }

    private boolean same(String x, String y) {
        return x != null && x.equals(y);
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}