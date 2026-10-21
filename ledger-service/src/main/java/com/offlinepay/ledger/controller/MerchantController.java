package com.offlinepay.ledger.controller;

import com.offlinepay.ledger.dto.CreditMerchantRequest;
import com.offlinepay.ledger.dto.MerchantBalanceResponse;
import com.offlinepay.ledger.dto.RegisterMerchantRequest;
import com.offlinepay.ledger.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * REST controller for the merchant ledger.
 *
 *   POST /api/v1/ledger/merchants/register            ← merchant app registers (idempotent)
 *   GET  /api/v1/ledger/merchants/{merchantId}        ← merchant app polls its collected balance
 *   POST /api/v1/ledger/merchants/{merchantId}/credit ← sync-service credits after settle
 */
@RestController
@RequestMapping("/api/v1/ledger/merchants")
@RequiredArgsConstructor
@Slf4j
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping("/register")
    public ResponseEntity<MerchantBalanceResponse> register(@Valid @RequestBody RegisterMerchantRequest request) {
        MerchantBalanceResponse response =
                merchantService.registerOrGet(request.getMerchantId(), request.getMerchantName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{merchantId}")
    public ResponseEntity<MerchantBalanceResponse> balance(@PathVariable String merchantId) {
        return ResponseEntity.ok(merchantService.getBalance(merchantId));
    }

    @PostMapping("/{merchantId}/credit")
    public ResponseEntity<MerchantBalanceResponse> credit(
            @PathVariable String merchantId,
            @Valid @RequestBody CreditMerchantRequest request) {
        MerchantBalanceResponse response = merchantService.credit(merchantId, request.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}