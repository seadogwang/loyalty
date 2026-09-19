package com.loyalty.access.security;

import com.loyalty.access.audit.AuditService;
import com.loyalty.access.authorization.AuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * OAuth2 resource-server setup (design M2-T01). Validates JWT signature + issuer +
 * audience + exp/nbf; maps subject to a {@link com.loyalty.common.context.PrincipalContext}
 * via {@link JwtContextResolver}; enforces RBAC via {@link AuthzEnforcementFilter} which
 * runs immediately after bearer-token authentication.
 *
 * <p>For local dev without an IdP, set {@code loyalty.security.jwt.symmetric-key} to a
 * shared secret (HS256); otherwise the decoder resolves JWKS from {@code issuer-uri}.
 */
@Configuration
@EnableConfigurationProperties(SecurityConfig.JwtProperties.class)
public class SecurityConfig {

    @Bean
    public AuthzEnforcementFilter authzEnforcementFilter(JwtContextResolver resolver,
                                                          AuthorizationService authz,
                                                          AuditService audit,
                                                          ObjectMapper json) {
        return new AuthzEnforcementFilter(resolver, authz, audit, json);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthzEnforcementFilter enforcementFilter,
                                                   JwtProperties jwtProperties) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(reg -> reg
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults()))
            .addFilterAfter(enforcementFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder;
        if (jwtProperties.getSymmetricKey() != null && !jwtProperties.getSymmetricKey().isBlank()) {
            SecretKeySpec key = new SecretKeySpec(
                    jwtProperties.getSymmetricKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            decoder = NimbusJwtDecoder.withSecretKey(key).build();
        } else {
            decoder = NimbusJwtDecoder.withIssuerLocation(jwtProperties.getIssuerUri()).build();
        }
        decoder.setJwtValidator(tokenValidator(jwtProperties));
        return decoder;
    }

    /** Issuer + exp + nbf (from default) combined with a strict audience check. */
    private static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties p) {
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(p.getIssuerUri());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<>(
                "aud", aud -> containsAudience(aud, p.getAudience()));
        return new DelegatingOAuth2TokenValidator<>(defaults, audience);
    }

    /** JWT audience claim may be a single String or a Collection. */
    private static boolean containsAudience(Object aud, String expected) {
        if (aud == null) {
            return false;
        }
        if (aud instanceof Collection<?> c) {
            return c.stream().anyMatch(x -> expected.equals(String.valueOf(x)));
        }
        return expected.equals(String.valueOf(aud));
    }

    @ConfigurationProperties(prefix = "loyalty.security.jwt")
    public static class JwtProperties {
        private String issuerUri = "http://localhost:9000/auth";
        private String audience = "loyalty-platform";
        private String symmetricKey;

        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getSymmetricKey() { return symmetricKey; }
        public void setSymmetricKey(String symmetricKey) { this.symmetricKey = symmetricKey; }
    }
}
