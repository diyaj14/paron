package com.offlinepay.ledger.service;

import com.offlinepay.ledger.dto.BalanceResponse;
import com.offlinepay.ledger.dto.FundingCreditRequest;
import com.offlinepay.ledger.exception.AccountNotFoundException;
import com.offlinepay.ledger.model.Account;
import com.offlinepay.ledger.model.FundingCredit;
import com.offlinepay.ledger.repository.AccountRepository;
import com.offlinepay.ledger.repository.FundingCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class FundingCreditService {
    private final AccountRepository accounts;
    private final FundingCreditRepository credits;

    @Transactional
    public BalanceResponse credit(FundingCreditRequest request) {
        Account account = accounts.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new AccountNotFoundException(request.getUserId()));
        // Check after acquiring this user's balance lock so concurrent retries for the
        // same funding ID observe the first committed event before adding a balance.
        FundingCredit existing = credits.findByFundingId(request.getFundingId()).orElse(null);
        if (existing == null) {
            BigDecimal amount = request.getAmount().setScale(2);
            account.setTotalBalance(account.getTotalBalance().add(amount));
            account.setAvailableBalance(account.getAvailableBalance().add(amount));
            credits.save(FundingCredit.builder().fundingId(request.getFundingId())
                    .userId(request.getUserId()).amount(amount)
                    .eventType("PAYPAL_CAPTURE_CREDIT").build());
        }
        BigDecimal reserved = account.getTotalBalance().subtract(account.getAvailableBalance());
        return BalanceResponse.builder().userId(account.getUserId()).totalBalance(account.getTotalBalance())
                .availableBalance(account.getAvailableBalance()).reservedAmount(reserved).build();
    }
}
