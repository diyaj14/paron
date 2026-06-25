package org.paron.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.paron.exception.ActiveTokenExsistsException;
import org.paron.exception.TokenNotFoundException;
import org.paron.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.paron.repo.TokenRepository;
import org.paron.security.JwtUtil;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


/*logics involved -issues offline jwt token, check user doesnt already have active token,
calls ledger to reserve funds,saves token in encrypted format in supabase,
return jwt to mobile app
*/
/* function of tokenservice is the following, get request from tokencontrolller to generate token
it calls jwtutil to generate token and ledger service to reserve fund*/

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;
    private final JwtUtil jwtUtil;
    private final LedgerServiceClient ledgerServiceClient; //Http client for ledger service
    @Value("${jwt.offline-token-expiry-hours}")
    private int defaultExpiryHours;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Logger.class);

    @Transactional //if anything happens in between rollback everything
    public TokenResponse generateToken(TokenRequest request) {
        log.info("Token request received for userId={}, amount={}",
                request.getUserId(), request.getAmount());

        // Prevent double-issuing
        if (tokenRepository.existsByUserIdAndStatus(request.getUserId(), TokenStatus.ACTIVE)) {
            throw new ActiveTokenExsistsException(request.getUserId());
        }
        //cal ledger for reserving fund,if fund not enough raise error
        String reservationId = ledgerServiceClient.reserveFunds(
                request.getUserId(),
                request.getAmount()
        );
        log.info("Funds reserved. reservationId={}", reservationId);
        //get expiry time
        int hours = request.getExpiryHours() > 0
                ? request.getExpiryHours()
                : defaultExpiryHours;
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(hours);

        //a placeholder fpr uuid replaced by jwt later
        TokenRecord record = TokenRecord.builder()
                .userId(request.getUserId())
                .reservationId(reservationId)
                .tokenValue("PENDING")          // placeholder — replaced in next step
                .maxAmount(request.getAmount())
                .spentAmount(BigDecimal.ZERO)
                .status(TokenStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();

        record = tokenRepository.save(record);
        //generate jwt with the help of uuid
        String jwt = jwtUtil.generateOfflineToken(
                request.getUserId(),
                record.getId(),
                reservationId,
                request.getAmount(),
                expiresAt
        );

        //update record
        record.setTokenValue(jwt);
        tokenRepository.save(record);
        log.info("Token issued. tokenId={}, userId={}", record.getId(), request.getUserId());

        //return response to app
        return TokenResponse.builder()
                .tokenId(record.getId())
                .token(jwt)                     // ← this gets stored on the device
                .userId(request.getUserId())
                .maxAmount(request.getAmount())
                .issuedAt(record.getCreatedAt())
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .message("Offline token issued. You can spend up to ₹"
                        + request.getAmount() + " while offline.")
                .build();
    }
// Called by sync-service before settling any transaction batch
    /*
     * Validates an offline spending token.
     *
     * Two-layer check:
     *  Layer 1 — Cryptographic: is the JWT signature valid? (JwtUtil)
     *  Layer 2 — Business:      is the DB record ACTIVE and not overspent?
     *
     * Both must pass. A valid JWT from a token that was already USED
     * (e.g. a replay attack) will fail Layer 2.
     */

    public ValidateTokenResponse validate(ValidateTokenRequest request) {
        log.info("Validating token for settlement, spentAmount={}", request.getSpentAmount());
        // ── Layer 1: Verify JWT signature and expiry
        /*statements of information (name/value pairs) about an entity (typically the user) and any additional metadata.
        could include-issuer,subject,audience,expiry time
         */
        Claims claims;
        try {
            claims = jwtUtil.validateAndExtractClaims(request.getToken());
        } catch (JwtException e) {
            log.warn("Jwt validation failed {}", e.getMessage());
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .reason(e.getClass().getSimpleName().toUpperCase())
                    .build();
        }
        // ── Layer 2: Check DB record status
        TokenRecord record = tokenRepository.findByTokenValue(request.getToken())
                .orElseThrow(() -> new TokenNotFoundException("JWT not found in database"));
        // Token must still be ACTIVE (not already USED, EXPIRED, or INVALIDATED)
        if (record.getStatus() != TokenStatus.ACTIVE) {
            log.warn("Token is not Active. status{}, tokenId={}", record.getStatus(), record.getId());

            return ValidateTokenResponse.builder()
                    .valid(false)
                    .reason("TOKEN_STATUS_" + record.getStatus().name())
                    .build();
        }
        // Spent amount must not exceed the max the user was allowed
        if (request.getSpentAmount().compareTo(record.getMaxAmount()) > 0) {
            log.warn("Spent amount {} exceeds max {}", request.getSpentAmount(), record.getMaxAmount());
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .reason("AMOUNT_EXCEEDS_LIMIT")
                    .build();
        }

        // All checks passed
        return ValidateTokenResponse.builder()
                .valid(true)
                .userId(record.getUserId())
                .reservationId(record.getReservationId())
                .maxAmount(record.getMaxAmount())
                .spentAmount(request.getSpentAmount())
                .build();

    }

    // 3. MARK AS USED
// Called by sync-service AFTER successful settlement
    @Transactional
    public void markAsUsed(@Valid MarkUsedRequest request) {
        TokenRecord record = tokenRepository.findByTokenValue(request.getToken())
                .orElseThrow(() -> new TokenNotFoundException("Token not found for mark-used"));
        record.setStatus(TokenStatus.USED);
        record.setSpentAmount(request.getFinalSpentAmount());
        record.setSettledAt(LocalDateTime.now());
        tokenRepository.save(record);
        log.info("Token marked as USED. tokenId={}, finalSpent={}",
                record.getId(), request.getFinalSpentAmount());
        /*So right now, money never actually leaves the bank. The ledger service needs
     a settle/deduct endpoint (e.g., POST /deduct) that would actually move the
     reserved funds out of the user's account. The markAsUsed should call that
     endpoint, or the sync-service should call it separately. */

    }

    // 4. TOKEN HISTORY
    // Called by the mobile app to show a user's past offline sessions
    public List<TokenResponse> getTokenHistory(String userId) {
        return tokenRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(record -> TokenResponse.builder()
                        .tokenId(record.getId())
                        .token(null)                // never return raw JWT in history
                        .userId(record.getUserId())
                        .maxAmount(record.getMaxAmount())
                        .issuedAt(record.getCreatedAt())
                        .expiresAt(record.getExpiresAt())
                        .status(record.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    // 5. SCHEDULED EXPIRY JOB
    // Runs every hour. Finds ACTIVE tokens past their expiry time and
    // flips them to EXPIRED, then tells ledger-service to release the reservation.
    /*
     * Cron expression "0 0 * * * *" = top of every hour.
     *
     * This prevents funds being locked forever if a user goes offline
     * but never reconnects to sync their transactions.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireStaleTokens() {
        log.info("Running token expiry cleanup job...");

        List<TokenRecord> expiredTokens = tokenRepository
                .findByStatusAndExpiresAtBefore(TokenStatus.ACTIVE, LocalDateTime.now());

        for (TokenRecord record : expiredTokens) {
            record.setStatus(TokenStatus.EXPIRED);
            tokenRepository.save(record);

            // Tell ledger-service to release the reservation so funds unlock
            try {
                ledgerServiceClient.releaseReservation(record.getReservationId());
                log.info("Expired token cleaned up. tokenId={}, userId={}",
                        record.getId(), record.getUserId());
            } catch (Exception e) {
                // Log but don't stop — process remaining tokens
                log.error("Failed to release reservation for tokenId={}. Will retry next run.",
                        record.getId(), e);
            }
        }
        log.info("Expiry job complete. {} tokens expired.", expiredTokens.size());
    }
}

