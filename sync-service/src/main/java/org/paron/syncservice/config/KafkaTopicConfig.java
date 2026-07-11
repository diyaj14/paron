package org.paron.syncservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.beans.factory.annotation.Value;
/*
        * Declares the Kafka topic this service uses.
        *
        * Spring Kafka will automatically create this topic on the broker at
 * startup if it doesn't already exist (assuming the broker allows
        * auto-creation, which Upstash and local docker-compose both do by default).
        *
        * partitions(3) — splits the topic into 3 independent lanes. Kafka
 * guarantees order WITHIN a partition, not across partitions. We don't
        * need global ordering across all users' transactions, only per-user
        * ordering matters for correctness, and Kafka's default partitioning
        * (by message key) sends all messages with the same key to the same
 * partition — so if we key by userId, each user's transactions stay
        * in order even though different users' transactions can process in
        * parallel across partitions.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.offline-transactions}")
    private String topicName;

    @Bean
    public NewTopic offlineTransactionsTopic(){
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();

    }
}
