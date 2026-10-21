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
import org.springframework.beans.factory.annotation.Value;
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

    // Hard per-user cap on total amount locked in ACTIVE reservations.
    // Injected from ledger.max-active-reserved (defaults to ₹500).
    @Value("${ledger.max-active-reserved:500.00}")
    private BigDecimal maxActiveReserved;


    // 1. RESERVE FUNDS
    // Called by token-service when a user requests an offline token.

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

        // The per-user cap check — never let a user hold more than the maximum
        // offline amount across ALL their active reservations at once.
        // Because we hold the account row lock (findByUserIdForUpdate above),
        // every reserve/release/settle for this user serializes through here,
        // so this sum is guaranteed race-free: a concurrent second request
        // cannot slip in between this check and the insert below.
        BigDecimal alreadyReserved = reservationRepository
                .sumReservedAmountByUserIdAndStatus(request.getUserId(), ReservationStatus.ACTIVE);
        if (alreadyReserved.add(request.getAmount()).compareTo(maxActiveReserved) > 0) {
            throw new ReserveLimitExceededException(
                    request.getUserId(), request.getAmount(), alreadyReserved, maxActiveReserved);
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

    // 2. RELEASE RESERVATION (full release — token expired unused)
    // Called by token-service's scheduled cleanup job.

    /*
     * Releases what is still locked on a reservation.
     *
     * Example: reserved ₹500, already settled ₹300 across earlier payments,
     * then the token expired with ₹200 never spent.
     *   -> the ₹200 (reserved minus already-settled) goes back to availableBalance
     *   -> if nothing is left unused (fully spent), the reservation just closes
     */
    @Transactional
    public void releaseReservation(ReleaseRequest request) {
        log.info("Release request: reservationId={}", request.getReservationId());

        Reservation reservation = findReservationOrThrow(request.getReservationId());
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationAlreadyClosedException(
                    request.getReservationId(), reservation.getStatus().name());
        }
        String reservationUserId = reservation.getUserId();

        // Lock the account row first — this serializes every balance change for
        // this user (reserve/release/settle), so no concurrent settle can slip in.
        Account account = accountRepository.findByUserIdForUpdate(reservationUserId)
                .orElseThrow(() -> new AccountNotFoundException(reservationUserId));

        // Re-read the reservation AFTER the account lock: by the time we got the
        // lock, another transaction may have settled more of this reservation,
        // so only the still-unsettled remainder is safe to return.
        reservation = findReservationOrThrow(request.getReservationId());
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationAlreadyClosedException(
                    request.getReservationId(), reservation.getStatus().name());
        }

        BigDecimal alreadySettled = reservation.getSettledAmount() != null
                ? reservation.getSettledAmount()
                : BigDecimal.ZERO;
        BigDecimal remainder = reservation.getReservedAmount().subtract(alreadySettled);

        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            // Return only the unspent part — the settled part is permanently gone
            account.setAvailableBalance(account.getAvailableBalance().add(remainder));
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);
            reservation.setStatus(ReservationStatus.RELEASED);
        } else {
            // Every payment was settled — nothing left to return, just close
            reservation.setStatus(ReservationStatus.SETTLED);
        }
        reservation.setClosedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation released. reservationId={}, amountReturned={}",
                  reservation.getId(), remainder);
    }

    // 3. SETTLE RESERVATION (partial release — actual spending happened)
    // Will be called by sync-service once it's built in the next stage.
    /*
     * Incrementally settles a reservation, one payment at a time.
     *
     * The reservation stays ACTIVE across multiple offline payments and only
     * closes when the whole reserved amount has been spent:
     *   reserved ₹500, payments arrive one by one:
     *     settle ₹125 -> total −125, ₹375 still locked, reservation stays ACTIVE
     *     settle ₹200 -> total −200, ₹175 still locked, reservation stays ACTIVE
     *     settle ₹175 -> total −175, ₹0 left, reservation closes (SETTLED)
     *
     * Unspent money is NOT returned here — it stays locked while the token is
     * still active. releaseReservation() returns it when the token closes.
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
        String reservationUserId = reservation.getUserId();

        Account account = accountRepository.findByUserIdForUpdate(reservationUserId)
                .orElseThrow(() -> new AccountNotFoundException(reservationUserId));

        // Re-read the reservation AFTER the account lock: the locked read
        // serializes concurrent settles for the same user, so settledAmount
        // here reflects every transaction that already committed.
        reservation = findReservationOrThrow(request.getReservationId());
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationAlreadyClosedException(
                    request.getReservationId(), reservation.getStatus().name());
        }

        BigDecimal alreadySettled = reservation.getSettledAmount() != null
                ? reservation.getSettledAmount()
                : BigDecimal.ZERO;
        BigDecimal remaining = reservation.getReservedAmount().subtract(alreadySettled);

        // A single payment must never push the reservation past its remaining
        // (already-settled = what earlier payments committed on this token)
        if (request.getSpentAmount().compareTo(remaining) > 0) {
            throw new LedgerException("SPENT_EXCEEDS_RESERVED",
                    "Spent amount " + request.getSpentAmount() +
                    " exceeds remaining reserved amount " + remaining);
        }

        // Permanently remove this payment from the total balance.
        // The unspent remainder stays locked (available unchanged) until the
        // token closes — releaseReservation() unlocks it then.
        account.setTotalBalance(account.getTotalBalance().subtract(request.getSpentAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        BigDecimal newSettled = alreadySettled.add(request.getSpentAmount());
        reservation.setSettledAmount(newSettled);

        boolean fullySpent = newSettled.compareTo(reservation.getReservedAmount()) >= 0;
        if (fullySpent) {
            reservation.setStatus(ReservationStatus.SETTLED);
            reservation.setClosedAt(LocalDateTime.now());
        }

        reservationRepository.save(reservation);

        log.info("Reservation settled incrementally. reservationId={}, cumulativeSpent={}, " +
                        "remainingLocked={}, fullySpent={}",
                  reservation.getId(), newSettled, remaining.subtract(request.getSpentAmount()), fullySpent);

        return SettleResponse.builder()
                .reservationId(request.getReservationId())
                .spentAmount(request.getSpentAmount())
                .releasedAmount(BigDecimal.ZERO)   // nothing unlocked at settle time
                .newTotalBalance(account.getTotalBalance())
                .newAvailableBalance(account.getAvailableBalance())
                .build();
    }

    // 4. CHECK BALANCE
    // Called by the mobile app to show "you can reserve up to ₹X offline".
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

    // Helper — shared lookup logic, since reservationId arrives as a String
    // over HTTP but is stored as a UUID primary key.

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
