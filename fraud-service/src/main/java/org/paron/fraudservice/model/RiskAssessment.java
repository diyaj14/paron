package org.paron.fraudservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable audit record. Shadow fields are observation-only. */
@Entity
@Table(name = "risk_assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String transactionId;
    private Double primaryScore;
    private String primaryModelVersion;
    private Double shadowScore;
    private String shadowModelVersion;
    private String decision;
    private String reasonCodes;
    @Column(nullable = false)
    private LocalDateTime assessedAt;
}
