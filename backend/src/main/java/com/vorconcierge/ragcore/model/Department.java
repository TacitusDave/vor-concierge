package com.vorconcierge.ragcore.model;

import java.time.Instant;

public record Department(
        Long id,
        Long orgId,
        Long parentDepartmentId,
        String name,
        Instant createdAt
) {}
