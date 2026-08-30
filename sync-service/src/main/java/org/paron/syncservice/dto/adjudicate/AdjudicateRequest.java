package org.paron.syncservice.dto.adjudicate;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/*
 * A dispute: two device receipts that contradict each other. The arbiter
 * loads both by their device-generated ids, re-verifies their signatures,
 * checks the token's authoritative spend state, and rules who has the
 * valid claim.
 */
@Data
public class AdjudicateRequest {

    @NotEmpty(message = "deviceTransactionIds is required — supply the two receipts under dispute")
    private List<String> deviceTransactionIds;
}