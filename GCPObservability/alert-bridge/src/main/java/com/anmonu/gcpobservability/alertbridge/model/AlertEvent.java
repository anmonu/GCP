package com.anmonu.gcpobservability.alertbridge.model;

import java.time.Instant;
import java.util.Map;

public record AlertEvent(
        String messageId,
        Instant publishTime,
        Map<String, String> attributes,
        String payload,
        Instant receivedAt
) {
}
