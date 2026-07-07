package org.paron.syncservice.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Triggers the settlement job on a fixed schedule.
 *
 * Why a schedule instead of running the job immediately every time
 * TransactionConsumer receives a Kafka message?
 * Running settlement per-message would mean calling ledger-service and
 * token-service for every single transaction the instant it arrives —
 * defeating the purpose of batching. Instead, transactions accumulate
 * in RECEIVED status, and every 30 seconds this scheduler picks up
 * whatever has accumulated and processes it as one or more chunks.
 *
 * This also makes the system resilient: if sync-service restarts, any
 * RECEIVED transactions are still sitting safely in the database and
 * will be picked up by the very next scheduled run — nothing is lost
 * just because it wasn't processed instantly.
 *
 * JobParametersBuilder with a timestamp is required because Spring
 * Batch treats a Job execution with identical parameters as "already
 * run" and refuses to run it again. Adding a changing parameter
 * (current time) makes every scheduled run a distinct, fresh execution.
 */
@EnableScheduling
@Component
@Slf4j
@RequiredArgsConstructor
public class SettlementJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    @Scheduled(fixedRate = 30000)
    public void runSettlementJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(settlementJob, params);

        } catch (Exception e) {
            log.error("Failed to launch settlement job", e);
        }
    }

}
