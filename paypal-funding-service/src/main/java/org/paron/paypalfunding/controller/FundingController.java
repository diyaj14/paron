package org.paron.paypalfunding.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.paron.paypalfunding.api.*;
import org.paron.paypalfunding.service.FundingService;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/funding") @RequiredArgsConstructor
public class FundingController {
    private final FundingService funding;
    @Value("${paypal.client-id}") private String clientId;
    @Value("${paypal.currency}") private String currency;
    @GetMapping("/paypal/config") public PaypalPublicConfig config() { return new PaypalPublicConfig(clientId, currency); }
    @PostMapping("/paypal/orders") public ResponseEntity<FundingResponse> create(@Valid @RequestBody CreateFundingRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(funding.create(request)); }
    @PostMapping("/paypal/orders/{orderId}/capture") public FundingResponse capture(@PathVariable String orderId) { return funding.capture(orderId); }
    @GetMapping("/{fundingId}") public FundingResponse status(@PathVariable UUID fundingId) { return funding.find(fundingId); }
}
