package org.paron.paypalfunding.repository;
import org.paron.paypalfunding.model.FundingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FundingAttemptRepository extends JpaRepository<FundingAttempt, UUID> {
    Optional<FundingAttempt> findByPaypalOrderId(String paypalOrderId);
}
