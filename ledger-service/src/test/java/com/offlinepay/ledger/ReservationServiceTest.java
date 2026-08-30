package com.offlinepay.ledger;

import com.offlinepay.ledger.dto.ReleaseRequest;
import com.offlinepay.ledger.dto.ReserveRequest;
import com.offlinepay.ledger.dto.ReserveResponse;
import com.offlinepay.ledger.dto.SettleRequest;
import com.offlinepay.ledger.dto.SettleResponse;
import com.offlinepay.ledger.exception.InsufficientFundsException;
import com.offlinepay.ledger.exception.LedgerException;
import com.offlinepay.ledger.model.Account;
import com.offlinepay.ledger.model.Reservation;
import com.offlinepay.ledger.model.ReservationStatus;
import com.offlinepay.ledger.repository.AccountRepository;
import com.offlinepay.ledger.repository.ReservationRepository;
import com.offlinepay.ledger.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .id(UUID.randomUUID())
                .userId("user_test_123")
                .totalBalance(new BigDecimal("2000.00"))
                .availableBalance(new BigDecimal("500.00"))
                .build();
        ReflectionTestUtils.setField(reservationService, "maxActiveReserved", new BigDecimal("500.00"));
    }

    @Test
    void reserveFunds_whenSufficientBalance_shouldSucceed() {
        // GIVEN — user has ₹500 available, requests ₹300
        ReserveRequest request = new ReserveRequest();
        request.setUserId("user_test_123");
        request.setAmount(new BigDecimal("300.00"));

        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));
        when(reservationRepository.sumReservedAmountByUserIdAndStatus("user_test_123", ReservationStatus.ACTIVE))
                .thenReturn(BigDecimal.ZERO);

        Reservation savedReservation = Reservation.builder()
                .id(UUID.randomUUID())
                .userId("user_test_123")
                .reservedAmount(new BigDecimal("300.00"))
                .status(ReservationStatus.ACTIVE)
                .build();

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        // WHEN
        ReserveResponse response = reservationService.reserveFunds(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.getReservedAmount()).isEqualByComparingTo("300.00");
        // available balance should now be 500 - 300 = 200
        assertThat(response.getRemainingAvailableBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void reserveFunds_whenInsufficientBalance_shouldThrowException() {
        // GIVEN — user has ₹500 available, requests ₹1000 (too much)
        ReserveRequest request = new ReserveRequest();
        request.setUserId("user_test_123");
        request.setAmount(new BigDecimal("1000.00"));

        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));

        // WHEN / THEN
        assertThatThrownBy(() -> reservationService.reserveFunds(request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("only ₹500.00 is available");
    }

    @Test
    void releaseReservation_whenActive_shouldReturnFundsToAvailable() {
        // GIVEN — an active reservation of ₹300, account currently has ₹200 available
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .userId("user_test_123")
                .reservedAmount(new BigDecimal("300.00"))
                .status(ReservationStatus.ACTIVE)
                .build();

        testAccount.setAvailableBalance(new BigDecimal("200.00"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));

        ReleaseRequest releaseRequest = new ReleaseRequest();
        releaseRequest.setReservationId(reservationId.toString());

        // WHEN
        reservationService.releaseReservation(releaseRequest);

        // THEN — available balance should be 200 + 300 = 500 again
        assertThat(testAccount.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void settleReservation_whenMultiplePayments_shouldAccumulateAndStayActive() {
        // GIVEN — ₹500 reserved, ₹125 already settled across earlier payments
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .userId("user_test_123")
                .reservedAmount(new BigDecimal("500.00"))
                .settledAmount(new BigDecimal("125.00"))
                .status(ReservationStatus.ACTIVE)
                .build();
        testAccount.setTotalBalance(new BigDecimal("2000.00"));
        testAccount.setAvailableBalance(new BigDecimal("0.00"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));

        SettleRequest request = new SettleRequest();
        request.setReservationId(reservationId.toString());
        request.setSpentAmount(new BigDecimal("200.00"));

        // WHEN — a second payment of ₹200 settles
        SettleResponse response = reservationService.settleReservation(request);

        // THEN — total debited, remainder stays locked, reservation stays ACTIVE
        assertThat(response.getSpentAmount()).isEqualByComparingTo("200.00");
        assertThat(testAccount.getTotalBalance()).isEqualByComparingTo("1800.00");
        assertThat(testAccount.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getSettledAmount()).isEqualByComparingTo("325.00");
    }

    @Test
    void settleReservation_whenFullySpent_shouldCloseReservation() {
        // GIVEN — ₹500 reserved, ₹325 already settled, ₹175 being paid now
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .userId("user_test_123")
                .reservedAmount(new BigDecimal("500.00"))
                .settledAmount(new BigDecimal("325.00"))
                .status(ReservationStatus.ACTIVE)
                .build();
        testAccount.setTotalBalance(new BigDecimal("2000.00"));

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));

        SettleRequest request = new SettleRequest();
        request.setReservationId(reservationId.toString());
        request.setSpentAmount(new BigDecimal("175.00"));

        reservationService.settleReservation(request);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.SETTLED);
        assertThat(reservation.getSettledAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void settleReservation_whenOverspendOnRemaining_shouldReject() {
        // GIVEN — only ₹50 remains, payment asks for ₹100
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .userId("user_test_123")
                .reservedAmount(new BigDecimal("500.00"))
                .settledAmount(new BigDecimal("450.00"))
                .status(ReservationStatus.ACTIVE)
                .build();

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(accountRepository.findByUserIdForUpdate("user_test_123"))
                .thenReturn(Optional.of(testAccount));

        SettleRequest request = new SettleRequest();
        request.setReservationId(reservationId.toString());
        request.setSpentAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> reservationService.settleReservation(request))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("exceeds remaining reserved amount");
    }
}
