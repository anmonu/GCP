package com.anmonu.gcpobservability.monitoringadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AlertPolicyRequest(
        @NotBlank String displayName,
        @NotBlank String metricType,
        String filter,
        @NotNull Double thresholdValue,
        int durationSeconds,
        boolean enabled,
        List<String> notificationChannels,
        String documentation
) {
}
