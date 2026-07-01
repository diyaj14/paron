package com.offlinepay.ledger.repository;

import com.offlinepay.ledger.model.Reservation;
import com.offlinepay.ledger.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // Used by /release and /settle — both receive a reservationId as a String
    // (it arrives as text over HTTP from token-service / sync-service)
//    Optional<Reservation> findById(UUID id);(its alreday there by jparepo

    List<Reservation> findByUserIdAndStatus(String userId, ReservationStatus status);
}
