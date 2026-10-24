package org.paron.tokenservice;

import org.paron.model.TokenRequest;
import org.paron.model.TokenResponse;
import org.paron.exception.ActiveTokenExsistsException;
import org.paron.model.TokenRecord;
import org.paron.model.TokenStatus;
import org.paron.model.ValidateTokenRequest;
import org.paron.model.ValidateTokenResponse;
import org.paron.repo.TokenRepository;
import org.paron.security.JwtUtil;
import org.paron.service.LedgerServiceClient;
import org.paron.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.jsonwebtoken.Claims;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Unit tests for TokenService.
 *
 * What is a unit test?
 * Testing ONE class in complete isolation.
 * We use Mockito to FAKE (mock) all the dependencies so
 * we don't need a real database or real ledger-service running.
 *
 * @Mock         = create a fake version of this class
 * @InjectMocks  = create a real TokenService, inject the fake dependencies into it
 */

@ExtendWith(MockitoExtension.class)
class TokenServiceTest{
    @Mock
    private TokenRepository tokenRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LedgerServiceClient ledgerServiceClient;
    @InjectMocks
    private TokenService tokenService;

    private TokenRequest validRequest;

    @BeforeEach
    void setUp(){
        validRequest = new TokenRequest();
        validRequest.setUserId("user_test_123");
        validRequest.setDeviceId("device_test_123");
        validRequest.setAmount(new BigDecimal("500.00"));
        validRequest.setExpiryHours(6);
    }

    @Test
    void issueToken_whenNoActiveToken_shouldSucced() {
        //given-user has no active token
        when(tokenRepository.existsByUserIdAndStatus("user_test_123", TokenStatus.ACTIVE))
                .thenReturn(false);
        //ledger-service returns a reservation ID
        when(ledgerServiceClient.reserveFunds("user_test_123", new BigDecimal("500.00")))
                .thenReturn("res_abc123");
        //Token repo returns the saved record with uuid
        TokenRecord savedRecord = TokenRecord.builder()
                .id(UUID.randomUUID())
                .userId("user_test_123")
                .deviceId("device_test_123")
                .reservationId("res_abc123")
                .tokenValue("PENDING")
                .maxAmount(new BigDecimal("500.00"))
                .status(TokenStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(6))
                .build();
        when(tokenRepository.save(any(TokenRecord.class))).thenReturn(savedRecord);
        when(jwtUtil.generateOfflineToken(any(), any(), any(), any(), any(), any()))
                .thenReturn("eyJmakeJWT123");
        // WHEN — we request a token
        TokenResponse response = tokenService.generateToken(validRequest);
        // THEN — token is issued
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("user_test_123");
        assertThat(response.getDeviceId()).isEqualTo("device_test_123");
        assertThat(response.getMaxAmount()).isEqualByComparingTo("500.00");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        // Verify ledger was called to reserve funds
        verify(ledgerServiceClient, times(1))
                .reserveFunds("user_test_123", new BigDecimal("500.00"));
    }
        @Test
        void issueToken_whenActiveTokenExists_shouldThrowException() {
            // GIVEN — user already has an active token
            when(tokenRepository.existsByUserIdAndStatus("user_test_123", TokenStatus.ACTIVE))
                    .thenReturn(true);

            //When-then -expect exception
        assertThatThrownBy(()->tokenService.generateToken(validRequest))
                .isInstanceOf(ActiveTokenExsistsException.class)
                .hasMessageContaining("already has an active offline token");

        //ledger shouldnt have been called
        verify(ledgerServiceClient,never()).reserveFunds(any(),any());


        }

        @Test
        void validate_whenDeviceDoesNotMatch_shouldReturnInvalid() {
            // GIVEN — a token bound to device_A, presented from device_B
            TokenRecord record = TokenRecord.builder()
                    .id(UUID.randomUUID())
                    .userId("user_test_123")
                    .deviceId("device_A")
                    .reservationId("res_abc123")
                    .tokenValue("eyJvalid")
                    .maxAmount(new BigDecimal("500.00"))
                    .spentAmount(BigDecimal.ZERO)
                    .status(TokenStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusHours(6))
                    .build();
            Claims matchingClaims = validClaims(record);
            when(jwtUtil.validateAndExtractClaims("eyJvalid")).thenReturn(matchingClaims);
            when(tokenRepository.findByTokenValue("eyJvalid")).thenReturn(java.util.Optional.of(record));

            ValidateTokenRequest request = new ValidateTokenRequest();
            request.setToken("eyJvalid");
            request.setSpentAmount(new BigDecimal("150.00"));
            request.setDeviceId("device_B");

            // WHEN
            ValidateTokenResponse response = tokenService.validate(request);

            // THEN — rejected on device binding, even though signature/status/amount are fine
            assertThat(response.isValid()).isFalse();
            assertThat(response.getReason()).isEqualTo("DEVICE_MISMATCH");
        }

        @Test
        void validate_whenDeviceMatches_shouldReturnValid() {
            // GIVEN — a token bound to device_A, presented from device_A
            TokenRecord record = TokenRecord.builder()
                    .id(UUID.randomUUID())
                    .userId("user_test_123")
                    .deviceId("device_A")
                    .reservationId("res_abc123")
                    .tokenValue("eyJvalid")
                    .maxAmount(new BigDecimal("500.00"))
                    .spentAmount(BigDecimal.ZERO)
                    .status(TokenStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusHours(6))
                    .build();
            Claims matchingClaims = validClaims(record);
            when(jwtUtil.validateAndExtractClaims("eyJvalid")).thenReturn(matchingClaims);
            when(tokenRepository.findByTokenValue("eyJvalid")).thenReturn(java.util.Optional.of(record));

            ValidateTokenRequest request = new ValidateTokenRequest();
            request.setToken("eyJvalid");
            request.setSpentAmount(new BigDecimal("150.00"));
            request.setDeviceId("device_A");

            // WHEN
            ValidateTokenResponse response = tokenService.validate(request);

            // THEN
            assertThat(response.isValid()).isTrue();
            assertThat(response.getUserId()).isEqualTo("user_test_123");
            assertThat(response.getReservationId()).isEqualTo("res_abc123");
        }
    private Claims validClaims(TokenRecord record) {
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(record.getUserId());
        when(claims.get("tokenId", String.class)).thenReturn(record.getId().toString());
        when(claims.get("reservationId", String.class)).thenReturn(record.getReservationId());
        when(claims.get("deviceId", String.class)).thenReturn(record.getDeviceId());
        when(claims.get("maxAmount", String.class)).thenReturn(record.getMaxAmount().toPlainString());
        return claims;
    }

}

