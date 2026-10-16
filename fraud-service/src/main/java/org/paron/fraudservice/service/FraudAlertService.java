package org.paron.fraudservice.service;

import lombok.RequiredArgsConstructor;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.exception.FraudException;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.repository.FraudAlertRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final FraudAlertRepository fraudAlertRepository;

    public List<FraudAlert> getAllAlerts() {
        return fraudAlertRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<FraudAlert> getAlertsByUserId(String userId) {
        return fraudAlertRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public FraudAlert reviewAlert(UUID alertId, String status, String reviewerNotes) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new FraudException("ALERT_NOT_FOUND", "Alert not found: " + alertId));

        alert.setStatus(status);
        alert.setReviewedAt(LocalDateTime.now());
        if (reviewerNotes != null) {
            alert.setReviewerNotes(reviewerNotes);
        }

        return fraudAlertRepository.save(alert);
    }
}
