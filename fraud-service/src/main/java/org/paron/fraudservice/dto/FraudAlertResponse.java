package org.paron.fraudservice.dto;

import lombok.Builder;
import lombok.Data;
import org.paron.fraudservice.model.RiskLevel;

import java.util.List;

@Data
@Builder
public class FraudAlertResponse {
    private String transactionId;
    private double score;
    private boolean approved;
    private RiskLevel riskLevel;
    private List<String> triggeredRules;
}
