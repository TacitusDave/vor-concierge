package com.vorconcierge.ragcore.model;

/**
 * Login-time projection joining a user to its current role, so AuthService can verify
 * credentials and mint a JWT in one lookup instead of two round trips.
 */
public record AuthProfile(
        Long userId,
        Long orgId,
        Long departmentId,
        Long roleId,
        String username,
        String email,
        String passwordHash,
        String totpSecret,
        boolean isPlatformOperator,
        boolean enabled,
        int hierarchyLevel,
        long roleCapabilityMask
) {}
