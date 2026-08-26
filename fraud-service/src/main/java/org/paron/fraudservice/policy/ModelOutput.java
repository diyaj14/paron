package org.paron.fraudservice.policy;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ModelOutput {
    private double score;
    private double confidence;
    private boolean fallback;
    private String modelVersion;
    private String thresholdPolicyVersion;
    private List<Contribution> topContributions;

    @Data
    @Builder
    public static class Contribution {
        private String reasonCode;
        private String plainLanguage;
        private double weight;
    }
}
