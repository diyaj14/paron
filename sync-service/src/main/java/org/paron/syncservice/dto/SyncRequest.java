package org.paron.syncservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.paron.syncservice.model.OfflineTransaction;

import java.util.List;

/*the mobile app sends a list of json body of transactions that
*would be done online
eg:-
*{
 *   "transactions": [
 *     {
 *       "deviceTransactionId": "device-uuid-1",
 *       "offlineToken": "eyJ...",
 *       "amount": 150.00,
 *       "merchantId": "merchant_abc",
 *       "transactedAt": "2024-01-15T10:30:00"
 *     },
 *     { ... more transactions made while offline ... }
 *   ]
 * }
 */

@Data
public class SyncRequest {

    @NotEmpty(message="transactions list cant be empty")
    @Valid
    private List<OfflineTransactionDto> transactions;
}
