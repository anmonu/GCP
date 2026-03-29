package com.anmonu.gcpobservability.monitoringadmin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChannelRequest(
        @NotBlank String displayName,
        @Email @NotBlank String emailAddress,
        boolean enabled
) {
}
