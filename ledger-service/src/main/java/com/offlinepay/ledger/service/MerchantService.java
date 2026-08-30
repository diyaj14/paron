package com.offlinepay.ledger.service;

import com.offlinepay.ledger.dto.MerchantBalanceResponse;
import com.offlinepay.ledger.exception.LedgerException;
import com.offlinepay.ledger.model.MerchantAccount;
import com.offlinepay.ledger.repository.MerchantAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/*
 * Merchant ledger — the "merchant side" of every settled offline payment.
 *
 * registerOrGet: idempotent registration. The same merchant ID can be sent
 *                many times (the merchant app retries); it only creates once.
 * credit:        called by sync-service after it settles a customer payment.
 *                Adds to the merchant's collectedBalance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantService {

    private static final String MISSING_MERCHANT = "MERCHANT_NOT_FOUND";

    private final MerchantAccountRepository merchantAccountRepository;

    @Transactional
    public MerchantBalanceResponse registerOrGet(String merchantId, String merchantName) {
        MerchantAccount existing = merchantAccountRepository.findByMerchantId(merchantId)
                .orElse(null);
        if (existing != null) {
            log.info("Merchant already registered. merchantId={}", merchantId);
            return toResponse(existing);
        }
        MerchantAccount saved = merchantAccountRepository.save(MerchantAccount.builder()
                .merchantId(merchantId)
                .merchantName(merchantName)
                .build());
        log.info("Merchant registered. merchantId={}", merchantId);
        return toResponse(saved);
    }

    public MerchantBalanceResponse getBalance(String merchantId) {
        MerchantAccount merchant = merchantAccountRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new LedgerException(MISSING_MERCHANT,
                        "Merchant not registered: " + merchantId));
        return toResponse(merchant);
    }

    @Transactional
    public MerchantBalanceResponse credit(String merchantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LedgerException("INVALID_AMOUNT", "Credit amount must be positive");
        }
        MerchantAccount merchant = merchantAccountRepository.findByMerchantIdForUpdate(merchantId)
                .orElseThrow(() -> new LedgerException(MISSING_MERCHANT,
                        "Merchant not registered: " + merchantId));
        merchant.setCollectedBalance(merchant.getCollectedBalance().add(amount));
        merchantAccountRepository.save(merchant);
        log.info("Merchant credited. merchantId={}, +{}", merchantId, amount);
        return toResponse(merchant);
    }

    private MerchantBalanceResponse toResponse(MerchantAccount merchant) {
        return MerchantBalanceResponse.builder()
                .merchantId(merchant.getMerchantId())
                .merchantName(merchant.getMerchantName())
                .collectedBalance(merchant.getCollectedBalance())
                .createdAt(merchant.getCreatedAt())
                .build();
    }
}