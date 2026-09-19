package com.loyalty.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Loyalty Engine Service (horizontally scalable compute): account + point runtime
 *  + tier/benefit runtime + rule (Drools) + order/event handling + recalculation. */
@SpringBootApplication(scanBasePackages = "com.loyalty")
@EnableScheduling
@EnableKafka
public class EngineServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineServiceApplication.class, args);
    }
}
