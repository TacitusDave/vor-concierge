package com.vorconcierge.ragcore.model;

import java.time.Instant;

public record User(
        Long id,
        Long orgId,
        Long departmentId,
        Long roleId,
        String username,
        String email,
        String passwordHash,
        String totpSecret,
        boolean isPlatformOperator,
        boolean enabled,
        Instant createdAt
) {}
