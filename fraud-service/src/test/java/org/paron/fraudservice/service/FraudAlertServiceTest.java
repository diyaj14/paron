package org.paron.fraudservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.fraudservice.exception.FraudException;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.repository.FraudAlertRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudAlertServiceTest {

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @InjectMocks
    private FraudAlertService fraudAlertService;

    private FraudAlert alert() {
        return FraudAlert.builder()
                .id(UUID.randomUUID())
                .transactionId("txn-1")
                .userId("user-1")
                .riskScore(0.8)
                .riskLevel(RiskLevel.HIGH)
                .status("PENDING")
                .build();
    }

    @Test
    void getAllAlerts() {
        when(fraudAlertRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(alert()));

        List<FraudAlert> result = fraudAlertService.getAllAlerts();

        assertEquals(1, result.size());
        assertEquals("txn-1", result.get(0).getTransactionId());
    }

    @Test
    void getAlertsByUserId() {
        when(fraudAlertRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(alert()));

        List<FraudAlert> result = fraudAlertService.getAlertsByUserId("user-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getUserId());
    }

    @Test
    void reviewAlertUpdatesStatusAndNotes() {
        UUID alertId = alert().getId();
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(alert()));
        when(fraudAlertRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FraudAlert result = fraudAlertService.reviewAlert(alertId, "REVIEWED", "Looks fine");

        assertEquals("REVIEWED", result.getStatus());
        assertEquals("Looks fine", result.getReviewerNotes());
        assertNotNull(result.getReviewedAt());
    }

    @Test
    void reviewAlertDoesNotOverwriteNotesWhenNull() {
        FraudAlert existing = alert();
        existing.setReviewerNotes("Existing note");
        UUID alertId = existing.getId();

        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.of(existing));
        when(fraudAlertRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FraudAlert result = fraudAlertService.reviewAlert(alertId, "REVIEWED", null);

        assertEquals("Existing note", result.getReviewerNotes());
    }

    @Test
    void reviewAlertThrowsWhenNotFound() {
        UUID alertId = UUID.randomUUID();
        when(fraudAlertRepository.findById(alertId)).thenReturn(Optional.empty());

        FraudException ex = assertThrows(FraudException.class,
                () -> fraudAlertService.reviewAlert(alertId, "REVIEWED", null));

        assertEquals("ALERT_NOT_FOUND", ex.getErrorCode());
    }
}
