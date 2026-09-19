package com.loyalty.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Shared infrastructure beans. UTC clock is the single source of "now". */
@Configuration
public class CommonConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
