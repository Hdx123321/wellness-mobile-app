package com.wellnessmate.plan.api;

import java.time.Instant;

public record SubscriberResponse(
    Long id, String username, String displayName, Instant subscribedAt
) {}
