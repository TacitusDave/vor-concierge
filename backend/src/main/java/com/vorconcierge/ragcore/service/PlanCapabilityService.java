package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A role's JWT capability_mask is embedded at login (cheap, DB-free per request), but the
 * organization's subscription plan can change at any time via Stripe (upgrade/downgrade/
 * cancellation) and must be enforced promptly — not "whenever the JWT happens to expire".
 * This caches the plan ceiling per org_id (far lower cardinality than per-user) with a short
 * TTL, so enforcement stays effectively real-time without a DB hit on every request.
 */
@Service
public class PlanCapabilityService {

    private static final long TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private record CacheEntry(long capabilityMask, String subscriptionStatus, long expiresAtMillis) {}

    private final OrganizationRepository organizationRepository;
    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public PlanCapabilityService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public long capabilityMaskFor(Long orgId) {
        return resolve(orgId).capabilityMask();
    }

    public boolean isSubscriptionUsable(Long orgId) {
        String status = resolve(orgId).subscriptionStatus();
        return "TRIALING".equals(status) || "ACTIVE".equals(status);
    }

    private CacheEntry resolve(Long orgId) {
        CacheEntry cached = cache.get(orgId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached;
        }
        var plan = organizationRepository.findPlanCapability(orgId)
                .orElseThrow(() -> new IllegalStateException("Unknown organization: " + orgId));
        CacheEntry fresh = new CacheEntry(plan.capabilityMask(), plan.subscriptionStatus(), now + TTL_MILLIS);
        cache.put(orgId, fresh);
        return fresh;
    }

    public void invalidate(Long orgId) {
        cache.remove(orgId);
    }
}
