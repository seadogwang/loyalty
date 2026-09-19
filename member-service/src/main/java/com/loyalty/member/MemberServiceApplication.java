package com.loyalty.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Member Service: member/identity/attribute/merge + master-data config
 *  (program/point_type/tier_scheme/tier/tier_rule/benefit). */
@SpringBootApplication(scanBasePackages = "com.loyalty")
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }
}
