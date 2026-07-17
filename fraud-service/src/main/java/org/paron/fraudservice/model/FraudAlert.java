package org.paron.fraudservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.kafka.common.Uuid;

import java.math.BigDecimal;
import java.util.UUID;

@AllArgsConstructor
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
    String riskScore;
    String riskLevel;
    String triggeredRules;
    String status;
    String createdAt;
    String reviewedAt;
    String reviewerNotes;
}
