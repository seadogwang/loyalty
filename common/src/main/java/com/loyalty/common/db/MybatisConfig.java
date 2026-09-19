package com.loyalty.common.db;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis mapper scanning for all services. Lives in {@code common} so any service that
 * scans {@code com.loyalty} (the default for business services) picks it up. The gateway
 * is isolated and does not scan this package, so it stays free of MyBatis/DB.
 */
@Configuration
@MapperScan("com.loyalty")
public class MybatisConfig {
}
