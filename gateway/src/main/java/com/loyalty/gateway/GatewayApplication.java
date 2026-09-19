package com.loyalty.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Edge gateway. Validates JWT, performs a permission pre-check, then routes to services
 *  by path prefix, resolving targets via Eureka service discovery. */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
