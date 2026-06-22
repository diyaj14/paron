package org.paron.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.paron.model.MarkUsedRequest;
import org.paron.model.TokenRequest;
import org.paron.model.TokenResponse;
import org.paron.model.ValidateTokenRequest;
import org.paron.repo.TokenRepository;
import org.paron.security.JwtUtil;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;


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

    @Transactional
    public TokenResponse generateToken(TokenRequest request) {
        log.info("Token request received for userId={}, amount={}",
                request.getUserId(), request.getAmount());

        // Prevent double-issuing
        if (tokenRepository.existsByUserIdAndStatus(request.getUserId(), TokenStatus.ACTIVE)) {
            throw new ActiveTokenExistsException(request.getUserId());
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


    public static void validate(ValidateTokenRequest request) {

    }

    public void markAsUsed(@Valid MarkUsedRequest request) {
    }
}
