package com.loyalty.common.access.domain;

/**
 * Resolved API-permission requirement for an inbound request (auth_api_permission).
 */
public record ApiPermission(
        String httpMethod,
        String pathTemplate,
        String permissionCode,
        ScopeType scopeType) {
}
