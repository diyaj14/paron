package org.paron.fraudservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.FraudCheckResponse;
import org.paron.fraudservice.dto.ReviewAlertRequest;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.service.FraudAlertService;
import org.paron.fraudservice.service.FraudScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final FraudScoringService fraudScoringService;
    private final FraudAlertService fraudAlertService;

    @PostMapping("/check")
    public ResponseEntity<FraudCheckResponse> check(@Valid @RequestBody TransactionEvent transaction) {
        FraudAlertResponse alertResponse = fraudScoringService.evaluate(transaction);

        // Surface the real three-state decision (APPROVE / HOLD_FOR_REVIEW / REJECT)
        // so the sync-service can hold suspicious transactions instead of
        // blindly rejecting them. Previously HOLD_FOR_REVIEW was collapsed
        // into REJECT here, making the sync-service's hold branch dead code.
        String decision = alertResponse.getDecision();

        List<String> triggered = alertResponse.getTriggeredRules();
        String reason = triggered.isEmpty() ? "NONE" : triggered.get(0);

        FraudCheckResponse response = FraudCheckResponse.builder()
                .score(alertResponse.getScore())
                .decision(decision)
                .reason(reason)
                .confidence(alertResponse.getConfidence())
                .modelVersion(alertResponse.getModelVersion())
                .policyVersion(alertResponse.getPolicyVersion())
                .reasonCodes(alertResponse.getReasonCodes())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlert>> getAlerts(
            @RequestParam(required = false) String userId) {
        List<FraudAlert> alerts = (userId != null)
                ? fraudAlertService.getAlertsByUserId(userId)
                : fraudAlertService.getAllAlerts();
        return ResponseEntity.ok(alerts);
    }

    @PutMapping("/alerts/{id}/review")
    public ResponseEntity<FraudAlert> reviewAlert(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewAlertRequest request) {
        FraudAlert alert = fraudAlertService.reviewAlert(id, request.getStatus(), request.getReviewerNotes());
        return ResponseEntity.ok(alert);
    }

    @PostMapping("/ping")
    public String online(){
        return "fraud check is active";
    }
}
