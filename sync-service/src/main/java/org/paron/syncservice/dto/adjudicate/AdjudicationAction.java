package org.paron.syncservice.dto.adjudicate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationAction {
    private String type; // REJECT_LOSER, MARK_HOLD, RELEASE_FUNDS, NO_ACTION
    private String transactionId;
    private String detail;
}