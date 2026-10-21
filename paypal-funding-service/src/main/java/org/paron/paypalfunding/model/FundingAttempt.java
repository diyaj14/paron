package org.paron.paypalfunding.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "paypal_funding_attempts", indexes = @Index(name = "idx_paypal_funding_user", columnList = "user_id"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FundingAttempt {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private String userId;
    @Column(name = "paypal_order_id", unique = true) private String paypalOrderId;
    @Column(name = "paypal_capture_id", unique = true) private String paypalCaptureId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FundingStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}
