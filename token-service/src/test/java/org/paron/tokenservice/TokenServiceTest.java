package org.paron.tokenservice;

import org.paron.model.TokenRequest;
import org.paron.model.TokenResponse;
import org.paron.exception.ActiveTokenExsistsException;
import org.paron.model.TokenRecord;
import org.paron.model.TokenStatus;
import org.paron.repo.TokenRepository;
import org.paron.security.JwtUtil;
import org.paron.service.LedgerServiceClient;
import org.paron.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                .reservationId("res_abc123")
                .tokenValue("PENDING")
                .maxAmount(new BigDecimal("500.00"))
                .status(TokenStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(6))
                .build();
        when(tokenRepository.save(any(TokenRecord.class))).thenReturn(savedRecord);
        when(jwtUtil.generateOfflineToken(any(), any(), any(), any(), any()))
                .thenReturn("eyJmakeJWT123");
        // WHEN — we request a token
        TokenResponse response = tokenService.generateToken(validRequest);
        // THEN — token is issued
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("user_test_123");
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
    }

