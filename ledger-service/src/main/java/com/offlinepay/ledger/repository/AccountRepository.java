package com.offlinepay.ledger.repository;

import com.offlinepay.ledger.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/*
 * Database access for the accounts table.
 *
 * findByUserIdForUpdate uses PESSIMISTIC_WRITE locking — explained in
 * ReservationService where it's actually used. In short: it stops two
 * simultaneous requests from both reading the same balance, both passing
 * the "sufficient funds" check, and both reserving — which would let a
 * user spend more than they actually have. The @Query is required here
 * because @Lock cannot attach to a plain derived-name method; it needs
 * an explicit JPQL query to apply the lock mode to.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") String userId);
}
