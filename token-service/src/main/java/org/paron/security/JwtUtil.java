package org.paron.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/*
 * Handles all JWT creation and verification for offline spending tokens.
 *
 * What is a JWT?
 * A JWT (JSON Web Token) is a signed string with 3 parts separated by dots:
 *   header.payload.signature
 *
 * The header says which algorithm was used (HS512 here).
 * The payload contains claims — our custom data like userId, amount, expiry.
 * The signature is a cryptographic hash that proves nobody tampered with it.
 *
 * Example decoded payload (what's inside our offline token):
 * {
 *   "sub":           "user_abc123",         <- subject = userId
 *   "tokenId":       "550e8400...",         <- our DB record ID
 *   "reservationId": "res_xyz789",
 *   "maxAmount":     "500.00",
 *   "iat":           1705312800,            <- issued at (unix timestamp)
 *   "exp":           1705334400             <- expires at (unix timestamp)
 * }
 */
@Component
@Slf4j
public class JwtUtil {

    // Secret key loaded from environment variable JWT_SECRET
    // Must be at least 64 characters for HS512
    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // Convert the secret string into a cryptographic key object
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a signed JWT for offline spending.
     *
     * Called by TokenService when a user requests an offline token.
     * The resulting string gets stored in DB and sent to the mobile device.
     *
     * @param userId         the user this token belongs to
     * @param tokenId        our DB record UUID (for correlation)
     * @param reservationId  the matching ledger reservation
     * @param maxAmount      maximum amount user can spend offline
     * @param expiresAt      when this token becomes invalid
     * @return               signed JWT string like "eyJhbGci..."
     */
    public String generateOfflineToken(
            String userId,
            UUID tokenId,
            String reservationId,
            BigDecimal maxAmount,
            LocalDateTime expiresAt) {

        Date expiryDate = Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant());

        return Jwts.builder()
                .subject(userId)                            // who owns this token
                .claim("tokenId",       tokenId.toString())
                .claim("reservationId", reservationId)
                .claim("maxAmount",     maxAmount.toPlainString())
                // We do NOT put spentAmount in the JWT because it changes
                // as the user makes payments. The DB is the source of truth for that.
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(secretKey, Jwts.SIG.HS512)        // sign with HS512 algorithm
                .compact();                                  // build the final JWT string
    }

    /**
     * Verifies a JWT and extracts its claims (payload data).
     *
     * Called during:
     *  1. Token validation by sync-service before settlement
     *  2. Merchant verification (optional — merchant scans QR, verifies token)
     *
     * @param token  the raw JWT string
     * @return       Claims object containing userId, maxAmount, etc.
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)      // use our secret key to verify signature
                .build()
                .parseSignedClaims(token)   // throws if signature invalid or expired
                .getPayload();              // returns the claims (userId, amount, etc.)
    }

    /**
     * Quick check — is this JWT's signature valid and not expired?
     * Does not throw, returns boolean. Used for fast pre-checks.
     */
    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired");
            return false;
        } catch (JwtException e) {
            log.warn("JWT token is invalid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts userId from JWT without throwing even if expired.
     * Useful for audit logging when we need to know who owned an expired token.
     */
    public String extractUserIdIgnoreExpiry(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException e) {
            // Even on expiry, the claims are still readable
            return e.getClaims().getSubject();
        }
    }
}