package com.anmonu.gcpobservability.monitoringadmin.dto;

import java.util.Map;

public record ChannelUpdateRequest(
        String displayName,
        Boolean enabled,
        Map<String, String> labels,
        String type
) {
}
