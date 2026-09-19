package com.loyalty.access.authorization;

import com.loyalty.access.domain.EffectiveGrant;
import com.loyalty.access.repository.AccessReadRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-TTL cache of a principal's effective grants (design 27.3). Keyed by principal id
 * only — the decision layer applies the tenant/program/scope filter per request, so the
 * cache is valid across scopes for the same principal. Invalidated eagerly on
 * grant/revoke.
 */
@Component
public class EffectiveGrantCache {

    private record Entry(List<EffectiveGrant> grants, Instant expiresAt) {}

    private final ConcurrentHashMap<UUID, Entry> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(30);

    private final AccessReadRepository repo;

    public EffectiveGrantCache(AccessReadRepository repo) {
        this.repo = repo;
    }

    public List<EffectiveGrant> get(UUID principalId) {
        if (principalId == null) {
            return List.of();
        }
        Instant now = Instant.now();
        Entry e = store.get(principalId);
        if (e != null && e.expiresAt().isAfter(now)) {
            return e.grants();
        }
        List<EffectiveGrant> fresh = repo.findEffectiveGrants(principalId);
        store.put(principalId, new Entry(fresh, now.plus(ttl)));
        return fresh;
    }

    public void invalidate(UUID principalId) {
        if (principalId != null) {
            store.remove(principalId);
        }
    }

    public void invalidateAll() {
        store.clear();
    }
}
