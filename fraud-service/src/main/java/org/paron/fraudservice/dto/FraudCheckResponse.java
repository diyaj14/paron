package org.paron.fraudservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudCheckResponse {
    private double score;
    private String decision;
    private String reason;
}
