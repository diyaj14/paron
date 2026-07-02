package org.paron.syncservice.controller;

/*
 * REST controller — the public API of sync-service.
 *
 * POST /api/v1/sync calls TransactionProducer.publish() for each
 * transaction and returns immediately with 202 Accepted. It does NOT
 * wait for settlement to complete — settlement happens asynchronously
 * via Kafka (TransactionConsumer) and the scheduled Spring Batch job
 * (SettlementJobScheduler). This is intentional: the device should not
 * be stuck waiting on a slow HTTP response while dozens of offline
 * transactions get validated and settled one by one.
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("api/v1/sync")
public class SyncController {

}
