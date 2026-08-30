package org.paron.syncservice.dto.adjudicate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
 * The AI judge's verdict ("judgement card").
 *
 * ruling                               meaning
 * -------                              -------
 * FORGED_RECEIPT          one/both receipts fail signature re-verification
 * SINGLE_PAYMENT          the duplicates are the same payment / one claim
 *                         already settled, other is a replay -> winner holds
 * DOUBLE_SPEND            both settled but joint spend exceeds the reserved
 *                         cap -> earlier payment stands, later one loses
 * MULTIPLE_LEGITIMATE     both genuine payments, jointly under the cap
 * INSUFFICIENT_EVIDENCE   receipts not found / not comparable — no ruling
 *
 * The ruling is always decided deterministically (the evidence gates), so a
 * verdict can never be a coin flip. If the LLM layer is enabled it only adds
 * the human-readable "summary" — it never changes the decision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationResponse {
    private String disputeId;
    private String ruling;
    private String winnerDeviceTransactionId;
    private String loserDeviceTransactionId;
    private BigDecimal amountInQuestion;
    private double confidence;
    private List<EvidenceCheck> evidence;
    private String summary;
    private boolean binding;
    private LocalDateTime adjudicatedAt;
}