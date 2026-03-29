package com.anmonu.gcpobservability.monitoringadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record PubsubChannelRequest(
        @NotBlank String displayName,
        @NotBlank String topicName,
        boolean enabled
) {
}
