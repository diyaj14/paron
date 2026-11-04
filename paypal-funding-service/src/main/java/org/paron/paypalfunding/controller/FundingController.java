package org.paron.paypalfunding.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.paron.paypalfunding.api.*;
import org.paron.paypalfunding.service.FundingService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController 
@RequestMapping("/api/funding") 
@RequiredArgsConstructor
public class FundingController {

    private final FundingService funding;

    @GetMapping("/paypal/config")
    public PaypalPublicConfig config() { return funding.publicConfig(); }

    @PostMapping("/paypal/orders") 
    public ResponseEntity<FundingResponse> create(@RequestHeader("X-User-Id") String authenticatedUserId,
                                                   @Valid @RequestBody CreateFundingRequest request) { 
        return ResponseEntity.status(HttpStatus.CREATED).body(funding.create(authenticatedUserId, request)); 
    }

    @PostMapping("/paypal/orders/{orderId}/capture") 
    public FundingResponse capture(@RequestHeader("X-User-Id") String authenticatedUserId, @PathVariable String orderId) {
        return funding.capture(authenticatedUserId, orderId);
    }

    @GetMapping("/{fundingId}") 
    public FundingResponse status(@RequestHeader("X-User-Id") String authenticatedUserId, @PathVariable UUID fundingId) {
        return funding.find(authenticatedUserId, fundingId);
    }
}
