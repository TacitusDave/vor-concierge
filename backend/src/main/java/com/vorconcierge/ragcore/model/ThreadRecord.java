package com.vorconcierge.ragcore.model;

import java.time.Instant;

public record ThreadRecord(
        Long id,
        Long orgId,
        Long userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {}
