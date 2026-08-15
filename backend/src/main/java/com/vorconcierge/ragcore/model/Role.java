package com.vorconcierge.ragcore.model;

public record Role(
        Long id,
        Long orgId,
        String name,
        int hierarchyLevel,
        long capabilityMask,
        boolean isDefault
) {}
