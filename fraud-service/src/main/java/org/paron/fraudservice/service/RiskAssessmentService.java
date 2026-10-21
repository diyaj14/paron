package org.paron.fraudservice.service;

import lombok.RequiredArgsConstructor;
import org.paron.fraudservice.model.RiskAssessment;
import org.paron.fraudservice.policy.DecisionResult;
import org.paron.fraudservice.policy.ModelOutput;
import org.paron.fraudservice.repository.RiskAssessmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskAssessmentService {
    private final RiskAssessmentRepository repository;

    public void record(String transactionId, DecisionResult decision, ModelOutput primary, List<String> reasonCodes) {
        ModelOutput.ShadowAssessment shadow = primary != null ? primary.getShadowAssessment() : null;
        repository.save(RiskAssessment.builder()
                .transactionId(transactionId)
                .primaryScore(primary != null ? primary.getScore() : null)
                .primaryModelVersion(primary != null ? primary.getModelVersion() : null)
                .shadowScore(shadow != null ? shadow.getScore() : null)
                .shadowModelVersion(shadow != null ? shadow.getModelVersion() : null)
                .decision(decision.getDecision().name())
                .reasonCodes(String.join(",", reasonCodes))
                .assessedAt(LocalDateTime.now())
                .build());
    }
}
