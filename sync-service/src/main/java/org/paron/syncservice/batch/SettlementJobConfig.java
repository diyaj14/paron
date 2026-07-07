package org.paron.syncservice.batch;

import lombok.RequiredArgsConstructor;
import org.paron.syncservice.model.OfflineTransaction;
import org.paron.syncservice.model.TransactionStatus;
import org.paron.syncservice.repository.OfflineTransactionRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SettlementJobConfig {

    private final OfflineTransactionRepository transactionRepository;
    private final SettlementProcessor          settlementProcessor;
    private final SettlementWriter             settlementWriter;

    /*
     * The reader — fetches every transaction currently sitting in
     * RECEIVED status, ready for settlement.
     *
     * We use a simple ListItemReader (load all matching rows into a list,
     * then read through it) rather than a cursor/paging reader, since for
     * a protype project the volume of RECEIVED transactions at
     * any one run is small. At real production scale (millions of rows),
     * you'd swap this for a JpaPagingItemReader so you never load more
     * than one page into memory at a time
     */
    @Bean
    public ItemReader<OfflineTransaction> settlementReader() {
        List<OfflineTransaction> receivedTransactions =
                transactionRepository.findByStatus(TransactionStatus.RECEIVED);
        return new ListItemReader<>(receivedTransactions);
    }
    @Bean
    public Step settlementStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               ItemReader<OfflineTransaction> settlementReader) {
        return new StepBuilder("settlementStep", jobRepository)
                .<OfflineTransaction, SettlementContext>chunk(50, transactionManager)
                .reader(settlementReader)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                // If a single item throws an unhandled exception, skip it
                // (log and move on) rather than failing the entire chunk —
                // one bad transaction shouldn't block 49 good ones in the
                // same chunk from settling.
                .faultTolerant()
                .skipLimit(50)
                .skip(Exception.class)
                .build();
    }

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }
}
