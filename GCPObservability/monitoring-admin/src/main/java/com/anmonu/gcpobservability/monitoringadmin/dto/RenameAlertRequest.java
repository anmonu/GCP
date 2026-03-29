package com.anmonu.gcpobservability.monitoringadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameAlertRequest(@NotBlank String displayName) {
}
