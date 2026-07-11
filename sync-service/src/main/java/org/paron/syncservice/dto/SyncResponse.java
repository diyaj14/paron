package org.paron.syncservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/*a response send after a sync req is made
(its a recipt not settlement)
 */
@Data
@Builder
public class SyncResponse {
    private  String message;
    private int acceptedCount;
    private List<String> acceptedDeviceTransactionIds;
}
