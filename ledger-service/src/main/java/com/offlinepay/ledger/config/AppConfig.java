package com.offlinepay.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
 * Application-wide configuration for ledger-service.
 *
 * Unlike token-service, this service does NOT currently call any other
 * service over HTTP — it's the "lowest" service in the chain (everyone
 * calls into it, it calls no one). So there's no RestTemplate bean here
 * yet, unlike token-service's AppConfig.
 *
 * That will likely change once sync-service exists, if ledger-service
 * ever needs to notify another service (e.g. publishing a "balance
 * changed" event). For now, this class is intentionally small.
 *
 * @EnableJpaAuditing is switched on here as a small but genuinely useful
 * addition: it lets JPA automatically manage timestamp fields (like
 * createdAt / updatedAt) without you manually calling
 * `setUpdatedAt(LocalDateTime.now())` everywhere in the service layer.
 * Right now ReservationService still sets these manually — this bean
 * is here so you can migrate to @CreatedDate / @LastModifiedDate
 * annotations on the entities later without adding new config.
 */
@Configuration
@EnableJpaAuditing
public class AppConfig {
    // No beans needed yet beyond enabling JPA auditing above.
    // Add a RestTemplate or WebClient bean here once this service
    // needs to call out to another service (e.g. notifying sync-service).
}
