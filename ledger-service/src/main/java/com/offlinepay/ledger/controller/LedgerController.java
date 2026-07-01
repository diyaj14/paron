package com.offlinepay.ledger.controller;

import com.offlinepay.ledger.dto.*;
import com.offlinepay.ledger.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * REST controller for ledger-service.
 *
 * Endpoints:
 *   POST /api/v1/ledger/reserve         ← token-service calls this
 *   POST /api/v1/ledger/release         ← token-service calls this (token expired)
 *   POST /api/v1/ledger/settle          ← sync-service will call this (next stage)
 *   GET  /api/v1/ledger/balance/{userId} ← mobile app calls this
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final ReservationService reservationService;

    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        ReserveResponse response = reservationService.reserveFunds(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/release")
    public ResponseEntity<String> release(@Valid @RequestBody ReleaseRequest request) {
        reservationService.releaseReservation(request);
        return ResponseEntity.ok("Reservation released successfully");
    }

    @PostMapping("/settle")
    public ResponseEntity<SettleResponse> settle(@Valid @RequestBody SettleRequest request) {
        SettleResponse response = reservationService.settleReservation(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String userId) {
        BalanceResponse response = reservationService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("ledger-service is running");
    }
}
