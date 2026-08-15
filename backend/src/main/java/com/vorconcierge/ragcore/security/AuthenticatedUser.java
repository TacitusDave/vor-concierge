package com.vorconcierge.ragcore.security;

public record AuthenticatedUser(
        Long userId,
        String username,
        Long orgId,
        Long departmentId,
        Long roleId,
        int hierarchyLevel,
        long roleCapabilityMask,
        boolean isPlatformOperator,
        String jwtId
) {}
