package org.paron.syncservice.repository;

import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfflineTransactionRepository extends JpaRepository<OfflineTransaction, UUID> {

    List<OfflineTransaction> findByUserIdOrderByReceivedAtDesc(String userId);

    List<OfflineTransaction> findByStatus(TransactionStatus status);
}
