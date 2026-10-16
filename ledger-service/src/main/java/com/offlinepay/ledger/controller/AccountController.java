package com.offlinepay.ledger.controller;

import com.offlinepay.ledger.exception.LedgerException;
import com.offlinepay.ledger.model.Account;
import com.offlinepay.ledger.repository.AccountRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;

/*
 * A small utility endpoint purely for development and testing.
 *
 * In a real bank-integrated system, accounts would be created and
 * synced automatically from the bank's core system — never via a
 * public API like this. This controller exists so that while you're
 * building and testing the project, you have a simple way to create
 * a test user with a starting balance, without manually inserting
 * rows into Supabase yourself.
 */
@RestController
@RequestMapping("/api/v1/ledger/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

    @PostMapping("/create-test-account")
    public ResponseEntity<Account> createTestAccount(@Valid @RequestBody CreateAccountRequest request) {
        if (accountRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new LedgerException("ACCOUNT_ALREADY_EXISTS",
                    "User " + request.getUserId() + " already has an account. Use /balance/{userId} to check it.");
        }

        Account account = Account.builder()
                .userId(request.getUserId())
                .totalBalance(request.getInitialBalance())
                .availableBalance(request.getInitialBalance())
                .build();

        Account saved = accountRepository.save(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Data
    static class CreateAccountRequest {
        @NotBlank(message = "userId is required")
        private String userId;

        @NotNull(message = "initialBalance is required")
        private BigDecimal initialBalance;
    }
}
