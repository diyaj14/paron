package com.offlinepay.ledger;

import com.offlinepay.ledger.dto.MerchantBalanceResponse;
import com.offlinepay.ledger.exception.LedgerException;
import com.offlinepay.ledger.model.MerchantAccount;
import com.offlinepay.ledger.repository.MerchantAccountRepository;
import com.offlinepay.ledger.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantAccountRepository merchantAccountRepository;

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void registerOrGet_whenNewMerchant_shouldCreate() {
        when(merchantAccountRepository.findByMerchantId("shop-1")).thenReturn(Optional.empty());
        MerchantAccount saved = MerchantAccount.builder()
                .id(UUID.randomUUID())
                .merchantId("shop-1")
                .merchantName("Shop One")
                .collectedBalance(BigDecimal.ZERO)
                .build();
        when(merchantAccountRepository.save(any(MerchantAccount.class))).thenReturn(saved);

        MerchantBalanceResponse response = merchantService.registerOrGet("shop-1", "Shop One");

        assertThat(response.getMerchantId()).isEqualTo("shop-1");
        assertThat(response.getCollectedBalance()).isEqualByComparingTo("0.00");
        verify(merchantAccountRepository, times(1)).save(any(MerchantAccount.class));
    }

    @Test
    void registerOrGet_whenAlreadyRegistered_shouldNotCreateAgain() {
        MerchantAccount existing = MerchantAccount.builder()
                .id(UUID.randomUUID())
                .merchantId("shop-1")
                .merchantName("Shop One")
                .collectedBalance(new BigDecimal("125.00"))
                .build();
        when(merchantAccountRepository.findByMerchantId("shop-1")).thenReturn(Optional.of(existing));

        MerchantBalanceResponse response = merchantService.registerOrGet("shop-1", "Shop One");

        assertThat(response.getCollectedBalance()).isEqualByComparingTo("125.00");
        verify(merchantAccountRepository, never()).save(any(MerchantAccount.class));
    }

    @Test
    void credit_whenRegistered_shouldAccumulate() {
        MerchantAccount merchant = MerchantAccount.builder()
                .id(UUID.randomUUID())
                .merchantId("shop-1")
                .merchantName("Shop One")
                .collectedBalance(new BigDecimal("125.00"))
                .build();
        when(merchantAccountRepository.findByMerchantIdForUpdate("shop-1")).thenReturn(Optional.of(merchant));
        when(merchantAccountRepository.save(any(MerchantAccount.class))).thenReturn(merchant);

        MerchantBalanceResponse response = merchantService.credit("shop-1", new BigDecimal("200.00"));

        assertThat(response.getCollectedBalance()).isEqualByComparingTo("325.00");
    }

    @Test
    void credit_whenNotRegistered_shouldThrow() {
        when(merchantAccountRepository.findByMerchantIdForUpdate("ghost-shop"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.credit("ghost-shop", new BigDecimal("10.00")))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("Merchant not registered");
    }
}