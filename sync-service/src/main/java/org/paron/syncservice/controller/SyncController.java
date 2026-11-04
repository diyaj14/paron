package org.paron.syncservice.controller;

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

    @PostMapping("/{userId}")
    public ResponseEntity<SyncResponse> syncTransactions(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String authenticatedUserId,
            @Valid @RequestBody SyncRequest request
            ){
        if (!userId.equals(authenticatedUserId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        log.info("Sync service recieved from userId={} with {} transaction", userId, request.getTransactions().size());

        SyncResponse response = syncService.submitTransactions(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<OfflineTransaction>> getStatus(@PathVariable String userId,
                                                               @RequestHeader("X-User-Id") String authenticatedUserId){
        if (!userId.equals(authenticatedUserId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        List<OfflineTransaction> transactions = syncService.getStatusForUser(userId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/admin/transactions")
    public ResponseEntity<List<OfflineTransaction>> getAdminTransactions(
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            @RequestHeader(value = "X-User-Id", required = false) String authenticatedUserId) {
        log.info("Admin fetching all offline transactions. user={}, roles={}", authenticatedUserId, roles);
        List<OfflineTransaction> transactions = syncService.getAllTransactions();
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("sync-service is running");
    }

    @PostMapping("/adjudicate")
    public ResponseEntity<AdjudicationResponse> adjudicate(
            @Valid @RequestBody AdjudicateRequest request) {
        log.info("Adjudication requested for {} receipt(s)", request.getDeviceTransactionIds().size());
        return ResponseEntity.ok(disputeAdjudicator.adjudicate(request));
    }
}
