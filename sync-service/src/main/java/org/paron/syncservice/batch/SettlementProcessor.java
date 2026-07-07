package org.paron.syncservice.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.paron.syncservice.dto.FraudCheckResult;
import org.paron.syncservice.dto.TokenValidationResult;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SettlementProcessor implements ItemProcessor<OfflineTransaction,SettlementContext> {
    private final IdempotencyService idempotencyService;
    private final TokenServiceClient tokenServiceClient;
    private final FraudCheckClient fraudCheckClient;
    /*
     * Returning null from an ItemProcessor tells Spring Batch to FILTER
     * this item out -- it will not be passed to the writer at all. We use
     * this for duplicates we want to silently ignore (no DB update needed,
     * since they were already settled the first time around).
     */

    @Override
    public SettlementContext process(OfflineTransaction transaction){
        log.info("Processing transaction for settlement. id={}, deviceTransactionId={}",
                transaction.getId(), transaction.getDeviceTransactionId());
        boolean claimed = idempotencyService.markAsProcessed(transaction.getDeviceTransactionId());

        if(!claimed){
            log.info("Transaction already claimed elsewhere, filtering out. deviceTransactionId={}",
                    transaction.getDeviceTransactionId());
            return null;
        }
        try{
            TokenValidationResult tokenResult =tokenServiceClient.validateToken(transaction.getOfflineToken(),transaction.getAmount());
            if(!tokenResult.isValid()){
                log.warn("Token validation failed. reason={}, deviceTransactionId={}",
                        tokenResult.getReason(), transaction.getDeviceTransactionId());
                transaction.setStatus(TransactionStatus.REJECTED);
                transaction.setRejectionReason("TOKEN_INVALID_" + tokenResult.getReason());
                idempotencyService.releaseClaim(transaction.getDeviceTransactionId());
                return new SettlementContext(transaction, null);
            }
            transaction.setUserId(tokenResult.getUserId());

            FraudCheckResult fraudResult = fraudCheckClient.check(transaction);
            transaction.setFraudScore(fraudResult.getScore());

            if(!fraudResult.isApproved()){
                log.warn("Fraud check rejected transaction. reason={}, deviceTransactionId={}", fraudResult.getReason(), transaction.getDeviceTransactionId());
                transaction.setStatus(TransactionStatus.REJECTED);
                transaction.setRejectionReason("FRAUD: " + fraudResult.getReason());
                idempotencyService.releaseClaim(transaction.getDeviceTransactionId());
                return new SettlementContext(transaction,null);
            }

            transaction.setStatus(TransactionStatus.PROCESSING);
            return new SettlementContext(transaction,tokenResult.getReservationId());
        } catch (Exception e) {
            log.error("Unexpected error during settlement processing. deviceTransactionId={}",
                    transaction.getDeviceTransactionId(), e);
            idempotencyService.releaseClaim(transaction.getDeviceTransactionId());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setRejectionReason("PROCESSING_ERROR: " + e.getMessage());
            return new SettlementContext(transaction, null);
        }

    }
}
