package com.offlinepay.ledger.repository;

import com.offlinepay.ledger.model.Reservation;
import com.offlinepay.ledger.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // Used by /release and /settle — both receive a reservationId as a String
    // (it arrives as text over HTTP from token-service / sync-service)
//    Optional<Reservation> findById(UUID id);(its alreday there by jparepo

    List<Reservation> findByUserIdAndStatus(String userId, ReservationStatus status);

    // Total amount currently locked in ACTIVE reservations for a user.
    // Used to enforce the per-user cap so a user can never hold more than
    // the max offline amount across all concurrently issued tokens.
    @Query("SELECT COALESCE(SUM(r.reservedAmount), 0) FROM Reservation r " +
           "WHERE r.userId = :userId AND r.status = :status")
    BigDecimal sumReservedAmountByUserIdAndStatus(@Param("userId") String userId,
                                                  @Param("status") ReservationStatus status);
}
