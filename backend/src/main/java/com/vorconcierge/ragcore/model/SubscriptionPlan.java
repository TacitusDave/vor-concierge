package com.vorconcierge.ragcore.model;

public record SubscriptionPlan(
        Long id,
        String code,
        String name,
        long capabilityMask,
        Integer maxUsers,
        Long maxStorageBytes,
        Integer maxDocuments,
        String stripePriceId
) {}
