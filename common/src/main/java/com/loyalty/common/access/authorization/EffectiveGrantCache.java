package com.loyalty.common.access.authorization;

import com.loyalty.common.access.domain.EffectiveGrant;
import com.loyalty.common.access.repository.AccessReadMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Effective-grant cache (design 27.3). Backed by Spring Cache abstraction:
 * <ul>
 *   <li>production: Redis ({@code spring.cache.type=redis}), key
 *       {@code effective-grants::{principalId}}, short TTL
 *       ({@code spring.cache.redis.time-to-live});</li>
 *   <li>tests: in-memory ({@code spring.cache.type=simple}) — no Redis needed.</li>
 * </ul>
 * Keyed by principal id only; the decision layer applies the tenant/program/scope filter
 * per request, so a cached entry is valid across scopes for the same principal.
 * Invalidated eagerly on grant/revoke.
 */
@Service
public class EffectiveGrantCache {

    private final AccessReadMapper mapper;

    public EffectiveGrantCache(AccessReadMapper mapper) {
        this.mapper = mapper;
    }

    @Cacheable(value = "effective-grants")
    public List<EffectiveGrant> load(UUID principalId) {
        if (principalId == null) {
            return List.of();
        }
        return mapper.findEffectiveGrants(principalId);
    }

    @CacheEvict(value = "effective-grants")
    public void invalidate(UUID principalId) {
        // no-op body; the @CacheEvict annotation performs the eviction.
    }
}
