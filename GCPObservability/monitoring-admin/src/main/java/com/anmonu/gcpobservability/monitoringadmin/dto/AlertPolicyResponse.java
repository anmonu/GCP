package com.anmonu.gcpobservability.monitoringadmin.dto;

import java.util.List;

public record AlertPolicyResponse(
        String name,
        String displayName,
        boolean enabled,
        String combiner,
        List<String> notificationChannels
) {
}
