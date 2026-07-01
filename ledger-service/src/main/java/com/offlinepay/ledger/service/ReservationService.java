package com.offlinepay.ledger.service;

import com.offlinepay.ledger.dto.*;
import com.offlinepay.ledger.exception.*;
import com.offlinepay.ledger.model.Account;
import com.offlinepay.ledger.model.Reservation;
import com.offlinepay.ledger.model.ReservationStatus;
import com.offlinepay.ledger.repository.AccountRepository;
import com.offlinepay.ledger.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Core business logic for ledger-service.
 *
 * This class is the "accountant" described earlier — it is the only
 * place in the whole system where account balances actually change.
 * Every method here runs inside a database transaction, so either
 * the whole operation succeeds, or none of it does.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationService {

    private final AccountRepository     accountRepository;
    private final ReservationRepository reservationRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. RESERVE FUNDS
    // Called by token-service when a user requests an offline token.
    // ─────────────────────────────────────────────────────────────────────────

    /*
     * Locks a portion of the user's available balance.
     *
     * Why @Transactional matters here: if the database write to "accounts"
     * succeeds but the write to "reservations" fails for any reason,
     * @Transactional rolls BOTH back. Without it, you could end up with
     * money "locked" in the account but no reservation record explaining
     * why — an inconsistent, unrecoverable state.
     *
     * Why findByUserIdForUpdate (pessimistic lock) matters:
     * Imagine the user fires two requests at almost the same instant
     * (e.g. a buggy mobile app retry, or two devices logged into the
     * same account). Without locking, both requests could:
     *   1. Read availableBalance = 500
     *   2. Both check "is 500 >= 400? yes" -> both pass
     *   3. Both reserve 400
     *   4. Final result: 800 reserved from a 500 balance — a bug that
     *      lets the user spend more than they have.
     * findByUserIdForUpdate places a database-level lock on that
     * specific row the moment it's read, so the second request must
     * WAIT until the first one fully finishes (commits) before it can
     * even read the balance. This guarantees the check-then-update is
     * atomic — no other request can sneak in between the check and the update.
     */
    @Transactional
    public ReserveResponse reserveFunds(ReserveRequest request) {
        log.info("Reserve request: userId={}, amount={}", request.getUserId(), request.getAmount());

        Account account = accountRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new AccountNotFoundException(request.getUserId()));

        // The core safety check — never let a user reserve more than they have
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    request.getUserId(), request.getAmount(), account.getAvailableBalance());
        }

        // Lock the funds: subtract from available, total stays unchanged
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        // Create the reservation record — this IS the "receipt" for the lock
        Reservation reservation = Reservation.builder()
                .userId(request.getUserId())
                .reservedAmount(request.getAmount())
                .status(ReservationStatus.ACTIVE)
                .build();
        reservation = reservationRepository.save(reservation);

        log.info("Funds reserved. reservationId={}, newAvailableBalance={}",
                  reservation.getId(), account.getAvailableBalance());

        return ReserveResponse.builder()
                .reservationId(reservation.getId().toString())   // UUID -> String for the HTTP response
                .reservedAmount(request.getAmount())
                .remainingAvailableBalance(account.getAvailableBalance())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RELEASE RESERVATION (full release — token expired unused)
    // Called by token-service's scheduled cleanup job.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void releaseReservation(ReleaseRequest request) {
        log.info("Release request: reservationId={}", request.getReservationId());

        Reservation reservation = findReservationOrThrow(request.getReservationId());

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationAlreadyClosedException(
                    request.getReservationId(), reservation.getStatus().name());
        }

        // Use the locking read here too — releasing also modifies the balance
        Account account = accountRepository.findByUserIdForUpdate(reservation.getUserId())
                .orElseThrow(() -> new AccountNotFoundException(reservation.getUserId()));

        // Give the full reserved amount back to available balance
        account.setAvailableBalance(account.getAvailableBalance().add(reservation.getReservedAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setClosedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation released. reservationId={}, amountReturned={}",
                  reservation.getId(), reservation.getReservedAmount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SETTLE RESERVATION (partial release — actual spending happened)
    // Will be called by sync-service once it's built in the next stage.
    // ─────────────────────────────────────────────────────────────────────────

    /*
     * Settles a reservation after offline spending.
     *
     * Example: reserved ₹500, user actually spent ₹300 offline.
     *   -> debit ₹300 permanently from totalBalance (the money is truly gone)
     *   -> the remaining ₹200 goes back to availableBalance (never spent, freed up)
     */
    @Transactional
    public SettleResponse settleReservation(SettleRequest request) {
        log.info("Settle request: reservationId={}, spentAmount={}",
                  request.getReservationId(), request.getSpentAmount());

        Reservation reservation = findReservationOrThrow(request.getReservationId());

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationAlreadyClosedException(
                    request.getReservationId(), reservation.getStatus().name());
        }

        if (request.getSpentAmount().compareTo(reservation.getReservedAmount()) > 0) {
            throw new LedgerException("SPENT_EXCEEDS_RESERVED",
                    "Spent amount " + request.getSpentAmount() +
                    " exceeds reserved amount " + reservation.getReservedAmount());
        }

        Account account = accountRepository.findByUserIdForUpdate(reservation.getUserId())
                .orElseThrow(() -> new AccountNotFoundException(reservation.getUserId()));

        BigDecimal leftover = reservation.getReservedAmount().subtract(request.getSpentAmount());

        // Permanently remove the spent amount from the total balance
        account.setTotalBalance(account.getTotalBalance().subtract(request.getSpentAmount()));
        // Return the unused leftover back to available balance
        account.setAvailableBalance(account.getAvailableBalance().add(leftover));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        reservation.setSettledAmount(request.getSpentAmount());
        reservation.setStatus(ReservationStatus.SETTLED);
        reservation.setClosedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation settled. reservationId={}, spent={}, leftReleased={}",
                  reservation.getId(), request.getSpentAmount(), leftover);

        return SettleResponse.builder()
                .reservationId(request.getReservationId())
                .spentAmount(request.getSpentAmount())
                .releasedAmount(leftover)
                .newTotalBalance(account.getTotalBalance())
                .newAvailableBalance(account.getAvailableBalance())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. CHECK BALANCE
    // Called by the mobile app to show "you can reserve up to ₹X offline".
    // ─────────────────────────────────────────────────────────────────────────

    public BalanceResponse getBalance(String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));

        BigDecimal reserved = account.getTotalBalance().subtract(account.getAvailableBalance());

        return BalanceResponse.builder()
                .userId(userId)
                .totalBalance(account.getTotalBalance())
                .availableBalance(account.getAvailableBalance())
                .reservedAmount(reserved)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — shared lookup logic, since reservationId arrives as a String
    // over HTTP but is stored as a UUID primary key.
    // ─────────────────────────────────────────────────────────────────────────

    private Reservation findReservationOrThrow(String reservationIdAsString) {
        UUID reservationId;
        try {
            reservationId = UUID.fromString(reservationIdAsString);
        } catch (IllegalArgumentException e) {
            throw new ReservationNotFoundException(reservationIdAsString);
        }

        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationIdAsString));
    }
}
