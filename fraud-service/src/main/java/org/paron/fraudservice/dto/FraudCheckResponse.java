package org.paron.fraudservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FraudCheckResponse {
    private double score;
    private String decision;
    private String reason;
    private Double confidence;
    private String modelVersion;
    private String policyVersion;
    private List<String> reasonCodes;
}
