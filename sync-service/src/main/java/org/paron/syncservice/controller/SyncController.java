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
import org.paron.syncservice.dto.SyncRequest;
import org.paron.syncservice.dto.SyncResponse;
import org.paron.syncservice.dto.adjudicate.AdjudicateRequest;
import org.paron.syncservice.dto.adjudicate.AdjudicationResponse;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.service.DisputeAdjudicator;
import org.paron.syncservice.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("api/v1/sync")
public class SyncController {

    private final SyncService syncService;
    private final DisputeAdjudicator disputeAdjudicator;

    /*queue to kafka for settling
     */
    @PostMapping("/{userId}")
    public ResponseEntity<SyncResponse> syncTransactions(
            @PathVariable String userId,
            @Valid @RequestBody SyncRequest request
            ){
        log.info("Sync service recieved from userId={} with {} transaction", userId, request.getTransactions().size());

        SyncResponse response = syncService.submitTransactions(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /* show confirmation to user about the total number of transactions
    that were recieved regardless of the status
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<OfflineTransaction>> getStatus(@PathVariable String userId){
              List<OfflineTransaction> transactions = syncService.getStatusForUser(userId);
              return ResponseEntity.ok(transactions);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("sync-service is running");
    }

    /*
     * The AI judge — resolve a dispute between two contradicting offline
     * receipts. Deterministic ruling + auditable evidence + optional LLM
     * narrative (see DisputeAdjudicator/DisputeReasoner).
     */
    @PostMapping("/adjudicate")
    public ResponseEntity<AdjudicationResponse> adjudicate(
            @Valid @RequestBody AdjudicateRequest request) {
        log.info("Adjudication requested for {} receipt(s)", request.getDeviceTransactionIds().size());
        return ResponseEntity.ok(disputeAdjudicator.adjudicate(request));
    }

}
