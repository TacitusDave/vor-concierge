package com.vorconcierge.ragcore.util;

import com.vorconcierge.ragcore.security.AuthenticatedUser;
import com.vorconcierge.ragcore.security.Capability;
import com.vorconcierge.ragcore.service.PlanCapabilityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Static-utility ergonomics (SecurityUtils.currentUser() called directly from controllers) are
 * kept intentionally rather than threading a security helper through every controller signature.
 * Capability checks need PlanCapabilityService, which this Spring-managed singleton wires into a
 * static field once at startup so the static methods can still use it.
 */
@Component
public final class SecurityUtils {

    private static PlanCapabilityService planCapabilityService;

    public SecurityUtils(PlanCapabilityService planCapabilityService) {
        SecurityUtils.planCapabilityService = planCapabilityService;
    }

    public static AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new SecurityException("Not authenticated");
        }
        return user;
    }

    public static void requirePlatformOperator() {
        AuthenticatedUser user = currentUser();
        if (!user.isPlatformOperator()) {
            throw new SecurityException("Platform operator permission required");
        }
    }

    /** Effective capability = what the user's role grants AND what the org's current plan allows. */
    public static boolean hasCapability(Capability capability) {
        AuthenticatedUser user = currentUser();
        if (user.isPlatformOperator()) {
            return true;
        }
        long planMask = planCapabilityService.capabilityMaskFor(user.orgId());
        return Capability.has(user.roleCapabilityMask() & planMask, capability);
    }

    public static void requireCapability(Capability capability) {
        if (!hasCapability(capability)) {
            throw new SecurityException(capability.name() + " capability required");
        }
    }

    /** Guards hand-rolled repository lookups-by-id against cross-tenant access (IDOR). */
    public static void requireSameOrg(Long resourceOrgId) {
        AuthenticatedUser user = currentUser();
        if (user.isPlatformOperator()) {
            return;
        }
        if (resourceOrgId == null || !resourceOrgId.equals(user.orgId())) {
            throw new SecurityException("Resource does not belong to your organization");
        }
    }
}
