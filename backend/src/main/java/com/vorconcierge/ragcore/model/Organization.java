package com.vorconcierge.ragcore.model;

import java.time.Instant;

public record Organization(
        Long id,
        String name,
        String slug,
        Long planId,
        String subscriptionStatus,
        Instant trialEndsAt,
        String stripeCustomerId,
        String stripeSubscriptionId,
        Instant createdAt
) {}
