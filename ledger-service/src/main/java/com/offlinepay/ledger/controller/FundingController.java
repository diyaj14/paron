package com.offlinepay.ledger.controller;

import com.offlinepay.ledger.dto.BalanceResponse;
import com.offlinepay.ledger.dto.FundingCreditRequest;
import com.offlinepay.ledger.service.FundingCreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Internal-only: called after PayPal capture verification, never by the browser. */
@RestController @RequestMapping("/api/v1/ledger/funding") @RequiredArgsConstructor
public class FundingController {
    private final FundingCreditService fundingCreditService;
    @PostMapping("/credit")
    public ResponseEntity<BalanceResponse> credit(@Valid @RequestBody FundingCreditRequest request) {
        return ResponseEntity.ok(fundingCreditService.credit(request));
    }
}
