package org.paron.syncservice.dto;

import java.util.List;

/*a response send after a sync req is made
(its a recipt not settlement)
 */
public class SyncResponse {
    private  String message;
    private int accpetedCount;
    private List<String> acceptedDeviceTransactionIds;
}
