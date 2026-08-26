package org.paron.fraudservice.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DecisionResult {
    private DecisionType decision;
    private String reason;
    private List<String> triggeredRules;
    private Double score;
    private Double confidence;
    private String modelVersion;
    private String policyVersion;
}
