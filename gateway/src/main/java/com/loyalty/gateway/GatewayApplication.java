package com.loyalty.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Edge gateway. Routes by path prefix; auth is enforced at each backend service (the
 *  shared enforcement layer in {@code common}). Gateway is isolated (no DB) so it does
 *  not scan {@code com.loyalty.common.access}. */
@SpringBootApplication(scanBasePackages = "com.loyalty.gateway")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
