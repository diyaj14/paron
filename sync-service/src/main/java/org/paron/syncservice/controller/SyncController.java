package org.paron.syncservice.controller;

/*
 * REST controller — the public API of sync-service.
 *
 * POST /api/v1/sync calls TransactionProducer.publish() for each
 * transaction and returns immediately with 202 Accepted. It does NOT
 * wait for settlement to complete — settlement happens asynchronously
 * via Kafka (TransactionConsumer) and the scheduled Spring Batch job
 * (SettlementJobScheduler). This is intentional: the device should not
 * be stuck waiting on a slow HTTP response while dozens of offline
 * transactions get validated and settled one by one.
 */

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.paron.syncservice.dto.SyncRequest;
import org.paron.syncservice.dto.SyncResponse;
import org.paron.syncservice.kafka.TransactionProducer;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("api/v1/sync")
public class SyncController {

    private final TransactionProducer transactionProducer;
    private final OfflineTransactionRepository transactionRepository;
    /*queue to kafka for settling
     */
    @PostMapping
    public ResponseEntity<SyncResponse> syncTransactions(
            @Valid @RequestBody SyncRequest request
            ){
        log.info("Sync service recieved with {} transaction",request.getTransactions().size());

        for(OfflineTransactionDto txn: request.getTransactions()){
            transactionProducer.publish(txn);
        }
        List<String> ids = request.getTransactions().stream()
                .map(OfflineTransactionDto::getDeviceTransactionId)
                .collect(Collectors.toList());

        SyncResponse response = SyncResponse.builder()
                .message("Transactions accepted and queued for settling")
                .acceptedCount(ids.size())
                .acceptedDeviceTransactionIds(ids)
                .build();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /* show confirmation to user about the total number of transactions
    that were recieved regardless of the status
     */
    @GetMapping
    public ResponseEntity<List<OfflineTransaction>> getStatus(@PathVariable String userId){
              List<OfflineTransaction> transactions = transactionRepository.findByUserIdOrderByReceivedAtDesc(userId);
              return ResponseEntity.ok(transactions);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("sync-service is running");
    }

}
