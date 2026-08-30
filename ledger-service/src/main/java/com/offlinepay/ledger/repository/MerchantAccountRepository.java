package com.offlinepay.ledger.repository;

import com.offlinepay.ledger.model.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, UUID> {

    Optional<MerchantAccount> findByMerchantId(String merchantId);

    // Locking read — serializes concurrent credit calls for the same merchant
    @Query("SELECT m FROM MerchantAccount m WHERE m.merchantId = :merchantId")
    Optional<MerchantAccount> findByMerchantIdForUpdate(@Param("merchantId") String merchantId);
}