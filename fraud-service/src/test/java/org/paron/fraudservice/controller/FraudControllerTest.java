package org.paron.fraudservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.paron.fraudservice.dto.FraudAlertResponse;
import org.paron.fraudservice.dto.ReviewAlertRequest;
import org.paron.fraudservice.dto.TransactionEvent;
import org.paron.fraudservice.model.FraudAlert;
import org.paron.fraudservice.model.RiskLevel;
import org.paron.fraudservice.service.FraudAlertService;
import org.paron.fraudservice.service.FraudScoringService;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class FraudControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FraudScoringService fraudScoringService = mock();
    private final FraudAlertService fraudAlertService = mock();

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new FraudController(fraudScoringService, fraudAlertService)).build();
    }

    private TransactionEvent validEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setUserId("user-1");
        event.setDeviceTransactionId("txn-1");
        event.setOfflineToken("token-1");
        event.setDeviceId("device-1");
        event.setAmount(new BigDecimal("100.00"));
        event.setTransactedAt("2026-07-28T15:00:00");
        event.setTokenExpiryTime("2026-07-28T16:00:00");
        return event;
    }

    // --- /check ---

    @Test
    void checkApproved() throws Exception {
        when(fraudScoringService.evaluate(any())).thenReturn(
                FraudAlertResponse.builder()
                        .transactionId("txn-1")
                        .score(0.0)
                        .approved(true)
                        .riskLevel(RiskLevel.LOW)
                        .triggeredRules(List.of())
                        .build()
        );

        mockMvc.perform(post("/api/v1/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEvent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0.0))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.reason").value("NONE"));
    }

    @Test
    void checkRejected() throws Exception {
        when(fraudScoringService.evaluate(any())).thenReturn(
                FraudAlertResponse.builder()
                        .transactionId("txn-1")
                        .score(0.8)
                        .approved(false)
                        .riskLevel(RiskLevel.HIGH)
                        .triggeredRules(List.of("AMOUNT_ANOMALY"))
                        .build()
        );

        mockMvc.perform(post("/api/v1/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEvent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0.8))
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.reason").value("AMOUNT_ANOMALY"));
    }

    @Test
    void reasonIsFirstTriggeredRule() throws Exception {
        when(fraudScoringService.evaluate(any())).thenReturn(
                FraudAlertResponse.builder()
                        .transactionId("txn-1")
                        .score(0.5)
                        .approved(false)
                        .riskLevel(RiskLevel.MEDIUM)
                        .triggeredRules(List.of("RULE_ONE", "RULE_TWO"))
                        .build()
        );

        mockMvc.perform(post("/api/v1/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEvent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("RULE_ONE"));
    }

    @Test
    void checkInvalidReturnsBadRequest() throws Exception {
        TransactionEvent invalid = new TransactionEvent();
        invalid.setAmount(new BigDecimal("-5.00"));

        mockMvc.perform(post("/api/v1/fraud/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    // --- /alerts ---

    @Test
    void getAllAlerts() throws Exception {
        FraudAlert alert = FraudAlert.builder()
                .id(UUID.randomUUID())
                .transactionId("txn-1")
                .userId("user-1")
                .riskScore(0.8)
                .riskLevel(RiskLevel.HIGH)
                .status("PENDING")
                .build();

        when(fraudAlertService.getAllAlerts()).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/fraud/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("txn-1"))
                .andExpect(jsonPath("$[0].riskScore").value(0.8));
    }

    @Test
    void getAlertsByUserId() throws Exception {
        FraudAlert alert = FraudAlert.builder()
                .id(UUID.randomUUID())
                .transactionId("txn-2")
                .userId("user-2")
                .riskScore(0.5)
                .riskLevel(RiskLevel.MEDIUM)
                .status("PENDING")
                .build();

        when(fraudAlertService.getAlertsByUserId("user-2")).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/fraud/alerts").param("userId", "user-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"));
    }

    // --- /alerts/{id}/review ---

    @Test
    void reviewAlert() throws Exception {
        UUID alertId = UUID.randomUUID();
        FraudAlert reviewed = FraudAlert.builder()
                .id(alertId)
                .transactionId("txn-1")
                .userId("user-1")
                .riskScore(0.8)
                .riskLevel(RiskLevel.HIGH)
                .status("REVIEWED")
                .reviewerNotes("Reviewed - legitimate")
                .reviewedAt(LocalDateTime.now())
                .build();

        when(fraudAlertService.reviewAlert(eq(alertId), eq("REVIEWED"), eq("Reviewed - legitimate")))
                .thenReturn(reviewed);

        ReviewAlertRequest request = new ReviewAlertRequest();
        request.setStatus("REVIEWED");
        request.setReviewerNotes("Reviewed - legitimate");

        mockMvc.perform(put("/api/v1/fraud/alerts/{id}/review", alertId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewerNotes").value("Reviewed - legitimate"));
    }

    // --- /ping ---

    @Test
    void pingReturnsActive() throws Exception {
        mockMvc.perform(post("/api/v1/fraud/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("fraud check is active"));
    }
}
