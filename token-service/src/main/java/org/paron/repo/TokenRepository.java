package org.paron.repo;

import org.paron.model.TokenRecord;
import org.paron.model.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*Database access layer for tokens */

@Repository
public interface TokenRepository extends JpaRepository<TokenRecord, UUID> {

    //find user's current active token
    Optional<TokenRecord> findByUserIdAndStatus(String userId, TokenStatus status);

    //find a token by its jwt value
    Optional<TokenRecord> findByTokenValue(String tokenValue);

    //find all tokens for user
    List<TokenRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    //Find all ACTIVE tokens that have passed their expiry time
    //Called by the scheduled cleanup job every hour
    List<TokenRecord> findByStatusAndExpiresAtBefore(TokenStatus status, LocalDateTime time);

    // Bulk update ACTIVE → EXPIRED for tokens past their expiry
    @Modifying
    @Query("UPDATE TokenRecord t SET t.status = 'EXPIRED' " +
            "WHERE t.status = 'ACTIVE' AND t.expiresAt < :now")
    int expireOldTokens(@Param("now") LocalDateTime now);

    // Check if a user already has an active token (prevent double-issuing)
    boolean existsByUserIdAndStatus(String userId, TokenStatus status);
}



