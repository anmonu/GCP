package com.anmonu.gcpobservability.monitoringadmin.dto;

import java.util.Map;

public record NotificationChannelResponse(
        String name,
        String displayName,
        String type,
        boolean enabled,
        Map<String, String> labels
) {
}
