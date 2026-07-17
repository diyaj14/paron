package org.paron.syncservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.paron.syncservice.dto.OfflineTransactionDto;
import org.paron.syncservice.dto.SyncRequest;
import org.paron.syncservice.dto.SyncResponse;
import org.paron.syncservice.exception.SyncException;
import org.paron.syncservice.kafka.TransactionProducer;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.paron.syncservice.service.SyncService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SyncServiceTest {

    @Mock
    private TransactionProducer transactionProducer;

    @Mock
    private OfflineTransactionRepository transactionRepository;

    @InjectMocks
    private SyncService syncService;

    private OfflineTransactionDto validTxn;

    @BeforeEach
    void setUp() {
        validTxn = new OfflineTransactionDto();
        validTxn.setDeviceTransactionId("device-txn-001");
        validTxn.setOfflineToken("eyJfaketoken");
        validTxn.setAmount(new BigDecimal("150.00"));
        validTxn.setMerchantId("merchant_abc");
        validTxn.setTransactedAt(LocalDateTime.now());
    }

    @Test
    void submitTransactions_whenValid_shouldPublishAndReturnAccepted(){
        SyncRequest request = new SyncRequest();
        request.setTransactions(List.of(validTxn));

        SyncResponse response=syncService.submitTransactions(request, "user_demo");

        assertThat(response.getAcceptedCount()).isEqualTo(1);
        assertThat(response.getAcceptedDeviceTransactionIds()).contains("device-txn-001");
        verify(transactionProducer,times(1)).publish(validTxn);
    }

    @Test
    void submitTransactions_whenBatchTooLarge_shouldThrowException() {

        // Build a list of 101 transactions (over the 100 limit)
        List<OfflineTransactionDto> bigBatch = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            OfflineTransactionDto txn = new OfflineTransactionDto();
            txn.setDeviceTransactionId("txn-" + i);
            txn.setOfflineToken("token");
            txn.setAmount(BigDecimal.TEN);
            txn.setTransactedAt(LocalDateTime.now());
            bigBatch.add(txn);
        }

        SyncRequest request = new SyncRequest();
        request.setTransactions(bigBatch);

        assertThatThrownBy(() -> syncService.submitTransactions(request, "user_demo"))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("Maximum 100 transactions");

        // Kafka should never be touched if batch validation fails
        verify(transactionProducer, never()).publish(any());
    }

    @Test
    void submitTransactions_whenOnePublishFails_shouldStillAcceptOthers() {
        OfflineTransactionDto txn2 = new OfflineTransactionDto();
        txn2.setDeviceTransactionId("device-txn-002");
        txn2.setOfflineToken("eyJfaketoken2");
        txn2.setAmount(new BigDecimal("200.00"));
        txn2.setTransactedAt(LocalDateTime.now());

        SyncRequest request = new SyncRequest();
        request.setTransactions(List.of(validTxn, txn2));

        // First publish succeeds, second throws
        doNothing().when(transactionProducer).publish(validTxn);
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(transactionProducer).publish(txn2);

        SyncResponse response = syncService.submitTransactions(request, "user_demo");

        // Only the first one was accepted
        assertThat(response.getAcceptedCount()).isEqualTo(1);
        assertThat(response.getAcceptedDeviceTransactionIds()).contains("device-txn-001");
        assertThat(response.getMessage()).contains("1 accepted");
        assertThat(response.getMessage()).contains("1 failed");
    }




}
