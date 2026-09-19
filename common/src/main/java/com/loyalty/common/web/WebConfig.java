package com.loyalty.common.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers shared web filters. {@link CorrelationIdFilter} runs before security so
 * the correlation id is available on the context even for rejected (401) requests.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-200);
        return registration;
    }
}
