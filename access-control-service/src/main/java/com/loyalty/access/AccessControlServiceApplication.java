package com.loyalty.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Access Control Service: OIDC/JWT, RBAC, API permission mapping, admin, audit, approval. */
@SpringBootApplication(scanBasePackages = "com.loyalty")
public class AccessControlServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccessControlServiceApplication.class, args);
    }
}
