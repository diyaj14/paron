package org.paron.fraudservice.repository;

import org.paron.fraudservice.model.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findByUserIdOrderByCreatedAtDesc(String userId);
    List<FraudAlert> findAllByOrderByCreatedAtDesc();
}
