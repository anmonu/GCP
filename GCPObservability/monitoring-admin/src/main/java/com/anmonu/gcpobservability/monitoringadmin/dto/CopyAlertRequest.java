package com.anmonu.gcpobservability.monitoringadmin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CopyAlertRequest(
        @NotBlank String displayName,
        Boolean enabled,
        String documentation,
        List<String> notificationChannels,
        Map<String, String> notificationChannelMapping
) {
}
