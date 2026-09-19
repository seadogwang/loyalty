package com.loyalty.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * V1 single bootable application hosting every service module against the shared
 * PostgreSQL database. Service modules remain independent jars; deploying them as
 * one process is a V1 simplification documented in ImplementationReport.md
 * (each service honors its logical write boundary internally).
 */
@SpringBootApplication
@EnableScheduling
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
