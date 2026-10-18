package com.offlinepay.ledger.repository;

import com.offlinepay.ledger.model.FundingCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FundingCreditRepository extends JpaRepository<FundingCredit, UUID> {
    Optional<FundingCredit> findByFundingId(UUID fundingId);
}
