package com.loyalty.access.domain;

/**
 * Principal kind (design 27.1). Derived from the JWT / trusted token; never accepted
 * from request bodies.
 */
public enum PrincipalType {
    USER,
    SERVICE_ACCOUNT,
    API_CLIENT
}
