package org.paron.syncservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/*
        * Result of a fraud check on a single transaction.
 *
         * Right now FraudCheckClient (in the next stage, fraud-service)
 * isn't built yet, so SettlementProcessor uses a temporary in-process
        * rule instead of calling a real fraud-service. This DTO's shape is
        * designed to match exactly what fraud-service will eventually return,
        * so swapping the in-process rule for a real HTTP call later is a
 * one-line change in SettlementProcessor — not a redesign.
 */
@Data
@Builder
public class FraudCheckResult {
    private double score;
    private boolean approved;
    private String reason;
    private String decision;
    private Double confidence;
    private String modelVersion;
    private String policyVersion;
    private List<String> reasonCodes;
}
