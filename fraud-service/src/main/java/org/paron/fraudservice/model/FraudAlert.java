package org.paron.fraudservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {
    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    UUID id;
    String transactionId;
    String userId;
    BigDecimal amount;
    double riskScore;
    RiskLevel riskLevel;
    String triggeredRules;
    String status;
    LocalDateTime createdAt;
    LocalDateTime reviewedAt;
    String reviewerNotes;
}
