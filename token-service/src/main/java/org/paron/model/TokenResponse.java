package org.paron.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * What the token-service sends back to the mobile app after issuing a token.
 *
 * Example JSON response:
 * {
 *   "tokenId":       "550e8400-e29b-41d4-a716-446655440000",
 *   "token":         "eyJhbGciOiJIUzUxMiJ9...",
 *   "userId":        "user_abc123",
 *   "maxAmount":     500.00,
 *   "expiresAt":     "2024-01-15T15:00:00",
 *   "status":        "ACTIVE"
 * }
 *
 * The "token" field is what gets stored on the device and shown to merchants.
 */
@Data
@Builder
public class TokenResponse {
    private UUID tokenId;           // DB primary key — useful for support queries
    private String token;           // The actual JWT string — store this on device
    private String userId;
    private BigDecimal maxAmount;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String status;
    private String message;         // Human-readable status message
}
 
