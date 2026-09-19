package com.loyalty.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Integration Service: external system ingress, normalization, idempotency, routing,
 *  Inbox/Outbox, Kafka. */
@SpringBootApplication(scanBasePackages = "com.loyalty")
public class IntegrationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }
}
